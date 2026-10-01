package com.sole.cinevault.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Process-level owner for an active CineVault Nearby host session.
 *
 * The HTTP server itself lives in [CineVaultNearbyRuntime]'s application singleton.
 * This foreground service keeps that process/session eligible to continue serving
 * while MainActivity is stopped (Home, Recents, calls, WhatsApp, screen off).
 */
class CineVaultNearbySharingService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Runtime is application-scoped. If Android recreated only the service after
        // the process was killed there is no safe file-sharing session to resurrect;
        // stop instead of advertising a phantom connection.
        if (!CineVaultNearbyRuntime.get(applicationContext).isSharingActive()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Nearby sharing",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps CineVault library sharing available while connected"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("CineVault Nearby")
            .setContentText("Sharing your library securely")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "cinevault_nearby_sharing"
        private const val NOTIFICATION_ID = 2515
    }
}
