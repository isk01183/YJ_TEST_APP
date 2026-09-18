package com.example.chargingapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Ultra V6 launcher.
 *
 * Known-Good Runtime을 유지하면서 앱 실행 즉시 마법진을 표시한다.
 * 서비스/BootReceiver/Full-screen notification은 사용하지 않는다.
 * 런타임 예외가 발생하면 다음 실행 때 저장된 crash report를 보여준다.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashReportStore.install(this)
        super.onCreate(savedInstanceState)

        val previousCrash = CrashReportStore.read(this)
        if (previousCrash != null) {
            showCrashReport(previousCrash)
        } else {
            runMagicRenderer()
        }
    }

    private fun runMagicRenderer() {
        val view = StellarSanctuaryView(this)

        registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )?.let(view::updateFromBatteryIntent)

        setContentView(view)
    }

    private fun showCrashReport(report: String) {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(Color.rgb(8, 10, 14))
        }

        column.addView(TextView(this).apply {
            text = "크래시 원인을 잡았습니다"
            setTextColor(Color.rgb(255, 205, 120))
            textSize = 24f
            setPadding(0, 0, 0, dp(16))
        }, matchWrap())

        column.addView(Button(this).apply {
            text = "오류 내용 클립보드에 복사"
            setOnClickListener {
                val clipboard =
                    getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("YJ_TEST_APP crash", report)
                )
                text = "복사됨"
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ))

        column.addView(Button(this).apply {
            text = "로그 삭제 후 마법진 다시 실행"
            setOnClickListener {
                CrashReportStore.clear(this@MainActivity)
                runMagicRenderer()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ).apply {
            topMargin = dp(10)
        })

        column.addView(TextView(this).apply {
            text = report
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(20), 0, dp(20))
        }, matchWrap())

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.rgb(8, 10, 14))
                addView(column)
            }
        )
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
