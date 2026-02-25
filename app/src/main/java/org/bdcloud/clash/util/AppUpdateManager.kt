package org.bdcloud.clash.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bdcloud.clash.R
import org.bdcloud.clash.api.ApiClient
import java.io.File

/**
 * Self-hosted in-app update manager.
 * Checks /app/version endpoint, shows update dialog, downloads APK with progress, installs.
 */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"

    fun checkForUpdate(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.service.getAppVersion()
                }
                val body = response.body() ?: return@launch
                if (!body.success || body.latestVersion == null) return@launch

                val currentVersion = getCurrentVersion(activity)
                if (isNewerVersion(body.latestVersion, currentVersion)) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(
                            activity,
                            body.latestVersion,
                            body.downloadUrl ?: return@withContext,
                            body.releaseNotes ?: "Bug fixes and improvements",
                            body.forceUpdate ?: false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Update check failed: ${e.message}")
                // Silently fail — don't bother user
            }
        }
    }

    private fun getCurrentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (_: Exception) { "0.0.0" }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (_: Exception) { }
        return false
    }

    private fun showUpdateDialog(
        activity: AppCompatActivity,
        version: String,
        downloadUrl: String,
        releaseNotes: String,
        forceUpdate: Boolean
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)
        val textVersion = dialogView.findViewById<TextView>(R.id.textUpdateVersion)
        val textNotes = dialogView.findViewById<TextView>(R.id.textReleaseNotes)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressDownload)
        val textProgress = dialogView.findViewById<TextView>(R.id.textDownloadProgress)

        textVersion.text = "Version $version available"
        textNotes.text = releaseNotes
        progressBar.visibility = android.view.View.GONE
        textProgress.visibility = android.view.View.GONE

        val builder = AlertDialog.Builder(activity, R.style.BdCloud_UpdateDialog)
            .setView(dialogView)
            .setCancelable(!forceUpdate)
            .setPositiveButton("Update Now") { dialog, _ ->
                // Show progress
                progressBar.visibility = android.view.View.VISIBLE
                textProgress.visibility = android.view.View.VISIBLE
                textProgress.text = "Downloading..."

                downloadAndInstall(activity, downloadUrl, progressBar, textProgress)
                // Don't dismiss — keep showing progress
                (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            }

        if (!forceUpdate) {
            builder.setNegativeButton("Later", null)
        }

        builder.show()
    }

    private fun downloadAndInstall(
        activity: AppCompatActivity,
        url: String,
        progressBar: ProgressBar,
        textProgress: TextView
    ) {
        val fileName = "bdcloud_update.apk"
        val downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, fileName)
        if (file.exists()) file.delete()

        val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("BDCLOUD Update")
            .setDescription("Downloading latest version...")
            .setDestinationUri(Uri.fromFile(file))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        val downloadId = downloadManager.enqueue(request)

        // Monitor download progress
        activity.lifecycleScope.launch {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val status = cursor.getInt(statusIdx)
                    val bytesDownloaded = cursor.getLong(bytesIdx)
                    val totalBytes = cursor.getLong(totalIdx)

                    withContext(Dispatchers.Main) {
                        when (status) {
                            DownloadManager.STATUS_RUNNING -> {
                                if (totalBytes > 0) {
                                    val percent = ((bytesDownloaded * 100) / totalBytes).toInt()
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = percent
                                    val mb = bytesDownloaded / (1024 * 1024f)
                                    val totalMb = totalBytes / (1024 * 1024f)
                                    textProgress.text = String.format("%.1f / %.1f MB (%d%%)", mb, totalMb, percent)
                                }
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                textProgress.text = "Installing..."
                                downloading = false
                                installApk(activity, file)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                textProgress.text = "Download failed"
                                downloading = false
                                Toast.makeText(activity, "Download failed. Try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                cursor.close()
                delay(500)
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            Toast.makeText(context, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
