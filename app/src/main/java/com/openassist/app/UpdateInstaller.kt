package com.openassist.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {
    const val CHANNEL_ID = "openassist_updates"
    const val NOTIFICATION_ID = 4040

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Updates"
            val descriptionText = "Notifications for OpenAssist updates and APK installation"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val TRUSTED_UPDATE_HOSTS = listOf(
        "github.com",
        "api.github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    fun isAllowedUpdateUrl(urlStr: String): Boolean {
        return try {
            val url = URL(urlStr)
            if (!url.protocol.equals("https", ignoreCase = true)) return false
            val host = url.host?.lowercase() ?: return false
            if (TRUSTED_UPDATE_HOSTS.contains(host)) return true
            host.endsWith(".github.com") || host.endsWith(".githubusercontent.com") || host.endsWith(".amazonaws.com")
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidApkArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 1024) return false
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null || zip.getEntry("classes.dex") != null
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads an APK with support for GitHub 302/307 redirects and real-time progress callbacks.
     */
    fun downloadApk(
        context: Context,
        apkUrl: String,
        versionTag: String,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit,
        onSuccess: (apkFile: File) -> Unit,
        onError: (message: String) -> Unit
    ) {
        Thread {
            try {
                if (!isAllowedUpdateUrl(apkUrl)) {
                    onError("Untrusted or insecure update URL: $apkUrl")
                    return@Thread
                }

                var currentUrl = apkUrl
                var connection: HttpURLConnection
                var redirectCount = 0

                // Follow GitHub redirects (GitHub releases redirect to AWS S3 pre-signed URLs)
                while (true) {
                    val urlObj = URL(currentUrl)
                    connection = urlObj.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 15000
                    connection.readTimeout = 25000
                    connection.setRequestProperty("User-Agent", "OpenAssist-InAppUpdater-$versionTag")

                    val status = connection.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 || status == 308
                    ) {
                        val newLocation = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (!newLocation.isNullOrBlank() && redirectCount < 7) {
                            if (!isAllowedUpdateUrl(newLocation)) {
                                throw SecurityException("Insecure or unauthorized redirect destination: $newLocation")
                            }
                            currentUrl = newLocation
                            redirectCount++
                            continue
                        } else {
                            throw Exception("Too many redirects or missing location header")
                        }
                    } else if (status in 200..299) {
                        break
                    } else {
                        throw Exception("Server returned HTTP $status while downloading update")
                    }
                }

                val totalBytes = connection.contentLengthLong
                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetFile = File(updateDir, "OpenAssist-${versionTag.replace('/', '_')}.apk")

                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(32 * 1024)
                var bytesReadTotal: Long = 0
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesReadTotal += read
                    val percent = if (totalBytes > 0) {
                        ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else {
                        -1
                    }
                    onProgress(bytesReadTotal, totalBytes, percent)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                if (targetFile.exists() && targetFile.length() > 0) {
                    if (isValidApkArchive(targetFile)) {
                        onSuccess(targetFile)
                    } else {
                        targetFile.delete()
                        onError("Downloaded update file is not a valid APK package")
                    }
                } else {
                    onError("Downloaded APK file is empty or missing")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Network error downloading update")
            }
        }.start()
    }

    /**
     * Creates an Intent to install the given APK file using FileProvider.
     */
    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Checks if the app can install packages (Android 8.0+).
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Creates intent to open unknown sources settings for this app.
     */
    fun createUnknownSourcesSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            null
        }
    }

    /**
     * Posts a system notification allowing the user to trigger the package installer.
     */
    fun postUpdateReadyNotification(context: Context, apkFile: File, releaseTag: String) {
        try {
            ensureNotificationChannel(context)
            val installIntent = createInstallIntent(context, apkFile)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("OpenAssist Update Ready")
                .setContentText("Tap to install OpenAssist $releaseTag")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {
            // Notification permission might not be granted; fallback to in-app banner
        }
    }

    /**
     * Dispatches notification indicating successful update/ready status
     */
    fun postUpdateSuccessNotification(context: Context, releaseTag: String) {
        try {
            ensureNotificationChannel(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("OpenAssist Installed")
                .setContentText("OpenAssist $releaseTag is successfully ready to use")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID + 1, builder.build())
        } catch (_: Exception) {
            // ignore
        }
    }
}
