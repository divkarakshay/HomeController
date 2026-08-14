package com.homecontroller.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat

/** Keeps the BLE link to the controller alive while the app is backgrounded,
 *  so the home-screen widget can toggle relays without opening the app. */
class RelayService : Service() {

    override fun onCreate() {
        super.onCreate()
        RelayHolder.init(this)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        RelayHolder.init(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        RelayHolder.disconnect()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        createChannel()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("HomeController")
            .setContentText("BLE link active — controls stay live")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Controller link",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        private const val CHANNEL_ID = "homecontroller"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RelayService::class.java)
            )
        }
    }
}