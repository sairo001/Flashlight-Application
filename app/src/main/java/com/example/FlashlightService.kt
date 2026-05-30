package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FlashlightService : Service() {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val NOTIFICATION_ID = 4529
        const val CHANNEL_ID = "flashlight_status_channel"
        const val CHANNEL_NAME = "Flashlight Quick Actions"

        const val ACTION_START = "com.example.flashlight.START"
        const val ACTION_TOGGLE = "com.example.flashlight.TOGGLE"
        const val ACTION_TURN_ON = "com.example.flashlight.TURN_ON"
        const val ACTION_TURN_OFF = "com.example.flashlight.TURN_OFF"
        const val ACTION_STOP = "com.example.flashlight.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        FlashlightController.initialize(this)
        createNotificationChannel()

        // Start collecting flashlight power state to update notification dynamically
        serviceScope.launch {
            FlashlightController.isTorchOn.collectLatest { isTorchOn ->
                updateNotification(isTorchOn)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                // Initial start - post default notification
                val isTorchOn = FlashlightController.isTorchOn.value
                startServiceForeground(buildNotification(isTorchOn))
            }
            ACTION_TOGGLE -> {
                FlashlightController.toggleTorch()
            }
            ACTION_TURN_ON -> {
                FlashlightController.setTorch(true)
            }
            ACTION_TURN_OFF -> {
                FlashlightController.setTorch(false)
            }
            ACTION_STOP -> {
                FlashlightController.setTorch(false)
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startServiceForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(isTorchOn: Boolean) {
        val notification = buildNotification(isTorchOn)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(isTorchOn: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, FlashlightService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FlashlightService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isTorchOn) "Flashlight is ON" else "Flashlight is OFF"
        val actionText = if (isTorchOn) "TURN OFF" else "TURN ON"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quick Flashlight")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_flashlight)
            .setOngoing(true)
            .setLocalOnly(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$statusText\nUse the quick toggle below to control the flashlight directly from your status bar."))
            .addAction(
                R.drawable.ic_flashlight,
                actionText,
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "DISMISS",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Enables toggling the device flashlight from the status bar tray."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
