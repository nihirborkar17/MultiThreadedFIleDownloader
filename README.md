# MultiThreadedFIleDownloader
Multithreaded File Downloader
A Java application that downloads files from the internet using multiple threads in parallel to improve download speed.
Overview
This application allows you to download files from URLs by splitting the download into multiple parts and downloading them concurrently using separate threads. After all parts are downloaded, they are merged back into a single file.
Features

Download files using multiple threads in parallel
Automatically determine file size and divide the work among threads
Display download progress for each thread
Merge file parts into the final file
Supports HTTP/HTTPS downloads
Handles redirects and standard HTTP responses

Requirements

Java 8 or higher
Internet connection

Usage
Compilation
bashjavac MultiThreadedDownloader.java
Running the Program
bashjava MultiThreadedDownloader <URL> <outputFilePath> <numThreads>
Where:

<URL> is the URL of the file you want to download
<outputFilePath> is the local path where you want to save the file
<numThreads> is the number of concurrent download threads to use

Example
git bash
  java MultiThreadedDownloader https://upload.wikimedia.org/wikipedia/commons/thumb/2/2d/Snake_River_%285mb%29.jpg/2560px-Snake_River_%285mb%29.jpg snake_river.jpg 4

This will download the Snake River image from Wikimedia Commons using 4 threads and save it as "snake_river.jpg".
How It Works

File Size Determination

The program first sends a request to determine the total file size


Chunking

The file is divided into equal-sized chunks based on the number of threads


Parallel Download

Each thread downloads its assigned chunk using HTTP Range requests
Each chunk is saved to a temporary file


Progress Tracking

Download progress is displayed for each thread


Merging

After all threads complete, the temporary files are merged into the final file


Cleanup

Temporary files are deleted after successful download



Troubleshooting
404 Error (Not Found)

Verify that the URL is correct and the file exists
Some URLs may not be directly accessible for download

403 Error (Forbidden)

Some servers may block download requests
The program includes User-Agent headers to mimic a web browser

Unable to Determine File Size

The program attempts multiple methods to determine the file size
If all methods fail, try a different URL

Connection Issues

Ensure you have a stable internet connection
Some servers may limit the number of concurrent connections

Recommended Test Files
For testing your downloader, you can use these reliable sources:

ThinkBroadband Test Files: http://ipv4.download.thinkbroadband.com/10MB.zip
Wikimedia Commons: https://upload.wikimedia.org/wikipedia/commons/thumb/2/2d/Snake_River_%285mb%29.jpg/2560px-Snake_River_%285mb%29.jpg
Apache Tomcat Binaries: https://downloads.apache.org/tomcat/tomcat-10/v10.1.17/bin/apache-tomcat-10.1.17.zip

Extension Ideas

Add GUI with progress bars
Implement pause/resume functionality
Add support for download retries
Create a download queue manager
Add bandwidth throttling options
