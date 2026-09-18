package com.example.chargingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity

/**
 * Galaxy Tab 안정성 확인용 메인 화면.
 *
 * 앱을 직접 실행했을 때 서비스/전체화면 알림/권한 요청을 시작하지 않고
 * 울트라 Canvas 마법진 렌더러와 배터리 정보만 표시한다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var magicView: StellarSanctuaryView

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                magicView.updateFromBatteryIntent(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemUi()

        magicView = StellarSanctuaryView(this)
        setContentView(magicView)

        registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )?.let(magicView::updateFromBatteryIntent)

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                batteryReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(batteryReceiver, filter)
        }
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
    }
}
