package com.example.chargingapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 앱 최초 실행 화면.
 * - 다른 앱 위에 표시(오버레이) 권한
 * - 배터리 최적화 예외 (항상 백그라운드에서 동작하기 위함)
 * - 알림 권한 (Android 13+)
 * 이 세 가지를 사용자가 한 번 허용하면, 이후로는 앱을 열지 않아도
 * ChargingForegroundService 가 재부팅 후에도 계속 동작합니다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btnWallpaper).setOnClickListener {
            // 잠금화면 배경 변경은 SET_WALLPAPER 는 일반 권한이라 자동 허용됨.
            // 여기서는 안내만 하고, 실제 배경 변경은 충전 시작 시 서비스가 자동으로 수행.
            tvStatus.text = "충전 케이블을 연결하면 잠금화면 배경이 자동으로 '충전중'으로 바뀝니다."
        }

        // Android 13+ 알림 권한 요청 (전체화면 알림을 띄우기 위해 필요)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }

        // 서비스 시작 (권한이 없어도 우선 시작 - 오버레이/전체화면은 권한 허용 후부터 동작)
        ChargingForegroundService.start(this)

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val overlayOk = Settings.canDrawOverlays(this)
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        tvStatus.text = if (overlayOk && batteryOk) {
            getString(R.string.status_all_ready)
        } else {
            getString(R.string.status_need_permission)
        }
    }
}
