package com.example.chargingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 전화 수신 화면과 같은 방식으로, 잠금화면이 걸려 있어도 화면 위에 표시되는 액티비티.
 * 충전이 시작되면 ChargingForegroundService 가 fullScreenIntent 알림으로 이 화면을 띄운다.
 * 충전이 끝나면(케이블 분리) 자동으로 닫힌다.
 */
class ChargingFullScreenActivity : AppCompatActivity() {

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_DISCONNECTED) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 잠금화면 위에 표시 + 화면 켜기 (구버전 호환용, API 27+ 는 manifest 속성으로도 처리됨)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContentView(R.layout.activity_charging_fullscreen)

        updateBatteryPercentText()

        val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(powerReceiver, filter)
        }

        // 화면 아무 곳이나 터치하면 닫기
        findViewById<android.view.View>(android.R.id.content).setOnClickListener {
            finish()
        }
    }

    private fun updateBatteryPercentText() {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        findViewById<TextView>(R.id.tvBatteryPercent).text = "$percent%"
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(powerReceiver) } catch (_: Exception) {}
    }
}
