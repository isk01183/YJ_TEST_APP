package com.example.chargingapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * 항상 백그라운드에서 실행되는 포그라운드 서비스.
 * - 부팅 시 BootReceiver 로부터 자동 시작됨
 * - START_STICKY 로 시스템이 죽여도 재시작 시도
 * - 충전기 연결/해제를 실시간 감지
 * - 인터넷을 전혀 사용하지 않음 (완전 오프라인)
 */
class ChargingForegroundService : Service() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onChargingStarted()
                Intent.ACTION_POWER_DISCONNECTED -> onChargingStopped()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID_SERVICE, buildServiceNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(powerReceiver, filter)
        }

        // 서비스가 시작된 시점에 이미 충전 중이면 바로 반영
        if (isCurrentlyCharging()) {
            onChargingStarted()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(powerReceiver) } catch (_: Exception) {}
        removeOverlay()
    }

    // ---------- 충전 시작/종료 처리 ----------

    private fun onChargingStarted() {
        showOverlayBadge()
        updateLockScreenWallpaper()
        showFullScreenChargingNotification()
    }

    private fun onChargingStopped() {
        removeOverlay()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_FULLSCREEN)
    }

    private fun isCurrentlyCharging(): Boolean {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    // ---------- ① 작은 오버레이 배지 (다른 앱 위에 "충전중" 표시) ----------

    private fun showOverlayBadge() {
        if (!Settings.canDrawOverlays(this)) return // 권한 없으면 표시 불가 (건너뜀)
        if (overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_charging, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 80

        windowManager?.addView(view, params)
        overlayView = view
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    // ---------- ② 잠금화면 배경화면을 "충전중" 이미지로 변경 ----------

    private fun updateLockScreenWallpaper() {
        try {
            val dm: DisplayMetrics = resources.displayMetrics
            val width = dm.widthPixels
            val height = dm.heightPixels

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.parseColor("#121212"))

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.parseColor("#34C759")
            paint.textSize = width * 0.16f
            paint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER

            val text = getString(R.string.charging_text)
            val textBounds = android.graphics.Rect()
            paint.getTextBounds(text, 0, text.length, textBounds)

            canvas.drawText(
                text,
                width / 2f,
                height / 2f - textBounds.exactCenterY(),
                paint
            )

            val wallpaperManager = WallpaperManager.getInstance(this)
            wallpaperManager.setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_LOCK
            )
        } catch (_: Exception) {
            // 일부 기기/런처는 잠금화면 배경 API를 지원하지 않을 수 있음 - 무시하고 계속 진행
        }
    }

    // ---------- ③ 잠금화면 위에 전체화면 "충전중" 화면 띄우기 (전화 수신 방식) ----------

    private fun showFullScreenChargingNotification() {
        val fullScreenIntent = Intent(this, ChargingFullScreenActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(getString(R.string.charging_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_FULLSCREEN, notification)
    }

    // ---------- 상시 실행을 위한 포그라운드 알림 (필수) ----------

    private fun buildServiceNotification(): android.app.Notification {
        createChannelIfNeeded()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "charging_channel"
        const val NOTIF_ID_SERVICE = 1
        const val NOTIF_ID_FULLSCREEN = 2

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
