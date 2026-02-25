package org.bdcloud.clash.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bdcloud.clash.R
import org.bdcloud.clash.api.ApiClient

/**
 * Polls /app/notifications and shows them as Android notifications.
 * Tracks seen notification IDs in SharedPreferences.
 */
object AppNotificationManager {

    private const val TAG = "AppNotifications"
    private const val CHANNEL_ID = "bdcloud_messages"
    private const val PREFS_KEY = "seen_notification_ids"

    fun checkNotifications(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.service.getNotifications()
                }
                val body = response.body() ?: return@launch
                if (!body.success || body.notifications.isNullOrEmpty()) return@launch

                val seenIds = getSeenIds(activity)
                val newNotifications = body.notifications.filter { it.id !in seenIds }

                if (newNotifications.isNotEmpty()) {
                    createNotificationChannel(activity)
                    newNotifications.forEach { notif ->
                        showNotification(activity, notif.id, notif.title, notif.message)
                    }
                    // Mark as seen
                    val updatedIds = seenIds + newNotifications.map { it.id }
                    saveSeenIds(activity, updatedIds.takeLast(100)) // keep last 100
                }
            } catch (e: Exception) {
                Log.d(TAG, "Notification check failed: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BDCLOUD Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from BDCLOUD"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, id: String, title: String, message: String) {
        val notificationId = id.hashCode()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun getSeenIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("bdcloud_notifs", Context.MODE_PRIVATE)
        return prefs.getStringSet(PREFS_KEY, emptySet()) ?: emptySet()
    }

    private fun saveSeenIds(context: Context, ids: List<String>) {
        val prefs = context.getSharedPreferences("bdcloud_notifs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet(PREFS_KEY, ids.toSet()).apply()
    }
}
