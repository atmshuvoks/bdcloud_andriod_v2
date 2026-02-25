package org.bdcloud.clash

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.bdcloud.clash.api.ApiClient

class BdCloudApp : Application() {

    companion object {
        const val VPN_CHANNEL_ID = "bdcloud_vpn_channel"
        const val VPN_NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VPN_CHANNEL_ID,
                "VPN Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows VPN connection status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
