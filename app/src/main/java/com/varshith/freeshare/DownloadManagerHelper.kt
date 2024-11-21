package com.varshith.freeshare

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadManagerHelper(private val context: Context) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun downloadFile(url: String, fileName: String, onProgressUpdate: (Float) -> Unit) {
        try {
            withContext(Dispatchers.IO) {
                // Create download request
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(getMimeType(url))
                    setTitle(fileName)
                    setDescription("Downloading file...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)

                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                    addRequestHeader("User-Agent", "Mozilla/5.0")
                }

                // Start download and get download ID
                val downloadId = downloadManager.enqueue(request)

                // Monitor download progress
                monitorDownloadProgress(downloadId, onProgressUpdate)
            }

            // Show toast on main thread
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
    }

    private fun monitorDownloadProgress(downloadId: Long, onProgressUpdate: (Float) -> Unit) {
        val query = DownloadManager.Query().setFilterById(downloadId)

        Thread {
            var isDownloading = true
            while (isDownloading) {
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val bytesTotal = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )

                    if (bytesTotal > 0) {
                        val progress = (bytesDownloaded * 100f / bytesTotal)
                        Handler(Looper.getMainLooper()).post {
                            onProgressUpdate(progress)
                        }
                    }

                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    if (status == DownloadManager.STATUS_SUCCESSFUL ||
                        status == DownloadManager.STATUS_FAILED) {
                        isDownloading = false
                    }
                }
                cursor.close()
                Thread.sleep(100) // Check progress every 100ms
            }
        }.start()
    }

    private fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}