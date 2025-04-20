import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MultiThreadedDownloader {
    private final String fileURL;
    private final String outputFilePath;
    private final int numThreads;
    private long fileSize;
    private final List<Future<Boolean>> downloadTasks = new ArrayList<>();
    private final ExecutorService executorService;

    public MultiThreadedDownloader(String fileURL, String outputFilePath, int numThreads) {
        this.fileURL = fileURL;
        this.outputFilePath = outputFilePath;
        this.numThreads = numThreads;
        this.executorService = Executors.newFixedThreadPool(numThreads);
    }

    public void download() throws IOException {
        // Step 1: Get file size
        fileSize = getFileSize();
        System.out.println("File size: " + fileSize + " bytes");

        // Step 2: Split into ranges
        long partSize = fileSize / numThreads;

        // Step 3: Create and submit download tasks
        for (int i = 0; i < numThreads; i++) {
            long startByte = i * partSize;
            long endByte = (i == numThreads - 1) ? fileSize - 1 : (i + 1) * partSize - 1;

            String partFileName = outputFilePath + ".part" + i;
            DownloadTask task = new DownloadTask(fileURL, partFileName, startByte, endByte, i);
            Future<Boolean> future = executorService.submit(task);
            downloadTasks.add(future);
        }

        // Step 4: Wait for all downloads to complete
        for (Future<Boolean> future : downloadTasks) {
            try {
                future.get(); // This blocks until the task completes
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Download failed: " + e.getMessage());
                e.printStackTrace();
                executorService.shutdownNow();
                return;
            }
        }

        // Step 5: Merge files
        mergeFiles();

        // Step 6: Clean up
        executorService.shutdown();
        deletePartFiles();

        System.out.println("Download completed successfully: " + outputFilePath);
    }

    private long getFileSize() throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(fileURL);
            connection = (HttpURLConnection) url.openConnection();

            // Try GET instead of HEAD as some servers don't support HEAD
            connection.setRequestMethod("GET");

            // Don't actually download the data
            connection.setRequestProperty("Range", "bytes=0-0");

            // Add User-Agent
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            // Enable redirect following
            connection.setInstanceFollowRedirects(true);

            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode / 100 != 2) {
                throw new IOException("HTTP error code: " + responseCode);
            }

            // Try to get size from Content-Range header first
            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange != null && contentRange.contains("/")) {
                String fileSizeStr = contentRange.substring(contentRange.indexOf('/') + 1);
                if (!fileSizeStr.equals("*")) {  // Sometimes size is unknown and marked with *
                    return Long.parseLong(fileSizeStr);
                }
            }

            // Fall back to Content-Length
            long contentLength = connection.getContentLengthLong();
            if (contentLength > 0) {
                return contentLength;
            }

            // If we can't determine the size, throw an exception
            throw new IOException("Unable to determine file size");
        } finally {
            if (connection != null) {
                // Make sure to close any input streams to avoid resource leaks
                try {
                    InputStream is = connection.getInputStream();
                    if (is != null) is.close();
                } catch (Exception ignored) {}

                connection.disconnect();
            }
        }
    }

    private void mergeFiles() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            byte[] buffer = new byte[8192]; // 8KB buffer

            for (int i = 0; i < numThreads; i++) {
                String partFileName = outputFilePath + ".part" + i;

                try (FileInputStream fis = new FileInputStream(partFileName)) {
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
    }

    private void deletePartFiles() {
        for (int i = 0; i < numThreads; i++) {
            File partFile = new File(outputFilePath + ".part" + i);
            if (partFile.exists()) {
                partFile.delete();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java MultiThreadedDownloader <URL> <outputFilePath> <numThreads>");
            return;
        }

        String url = args[0];
        String outputFile = args[1];
        int threads = Integer.parseInt(args[2]);

        try {
            MultiThreadedDownloader downloader = new MultiThreadedDownloader(url, outputFile, threads);
            downloader.download();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class DownloadTask implements Callable<Boolean> {
        private final String fileURL;
        private final String outputFile;
        private final long startByte;
        private final long endByte;
        private final int partNumber;

        public DownloadTask(String fileURL, String outputFile, long startByte, long endByte, int partNumber) {
            this.fileURL = fileURL;
            this.outputFile = outputFile;
            this.startByte = startByte;
            this.endByte = endByte;
            this.partNumber = partNumber;
        }

        @Override
        public Boolean call() throws Exception {
            HttpURLConnection connection = null;
            RandomAccessFile file = null;

            try {
                URL url = new URL(fileURL);
                connection = (HttpURLConnection) url.openConnection();

                // Add User-Agent to mimic a browser
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

                // Set range header to download only specific bytes
                String rangeHeader = "bytes=" + startByte + "-" + endByte;
                connection.setRequestProperty("Range", rangeHeader);

                // Connect to server
                connection.connect();

                // Check for proper response code (206 Partial Content or 200 OK)
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_PARTIAL && responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode);
                }

                // Create part file
                file = new RandomAccessFile(outputFile, "rw");

                long bytesToDownload = endByte - startByte + 1;
                long bytesDownloaded = 0;

                try (InputStream inputStream = connection.getInputStream()) {
                    byte[] buffer = new byte[8192]; // 8KB buffer
                    int bytesRead;

                    while (bytesDownloaded < bytesToDownload && (bytesRead = inputStream.read(buffer)) != -1) {
                        file.write(buffer, 0, bytesRead);
                        bytesDownloaded += bytesRead;

                        // Print progress (optional)
                        double progress = (double) bytesDownloaded / bytesToDownload * 100;
                        System.out.printf("Part %d: %.2f%% complete\n", partNumber, progress);
                    }
                }

                System.out.printf("Part %d download complete. Downloaded %d bytes.\n", partNumber, bytesDownloaded);
                return true;

            } finally {
                if (file != null) {
                    file.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}