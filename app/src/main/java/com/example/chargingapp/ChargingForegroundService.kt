package com.example.chargingapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 충전 상태를 감시하는 포그라운드 서비스.
 *
 * 이전 버전의 초록색 잠금화면 배경 변경과 작은 오버레이 배지는 제거했다.
 * 충전이 시작되면 동일한 StellarSanctuaryView를 사용하는 전체화면 Activity를 호출한다.
 */
class ChargingForegroundService : Service() {

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> showChargingScreen()
                Intent.ACTION_POWER_DISCONNECTED -> cancelChargingNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(powerReceiver, filter)
        }

        if (isCurrentlyCharging()) {
            showChargingScreen()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: Exception) {
        }
        cancelChargingNotification()
    }

    private fun isCurrentlyCharging(): Boolean {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.isCharging
    }

    private fun showChargingScreen() {
        val activityIntent = Intent(this, ChargingFullScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHARGING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(getString(R.string.charging_notification_title))
            .setContentText(getString(R.string.charging_notification_text))
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(CHARGING_NOTIFICATION_ID, notification)
    }

    private fun cancelChargingNotification() {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(CHARGING_NOTIFICATION_ID)
    }

    private fun buildServiceNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            1002,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text))
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_channel_description)
            setShowBadge(false)
        }

        val chargingChannel = NotificationChannel(
            CHARGING_CHANNEL_ID,
            getString(R.string.charging_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.charging_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(chargingChannel)
    }

    companion object {
        private const val SERVICE_CHANNEL_ID = "charging_service_v2"
        private const val CHARGING_CHANNEL_ID = "charging_fullscreen_v2"

        private const val SERVICE_NOTIFICATION_ID = 1
        private const val CHARGING_NOTIFICATION_ID = 2

        fun start(context: Context) {
            val intent = Intent(context, ChargingForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
