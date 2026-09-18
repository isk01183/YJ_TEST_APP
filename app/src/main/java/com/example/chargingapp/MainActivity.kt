package com.example.chargingapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashReportStore.install(this)
        super.onCreate(savedInstanceState)

        val previousCrash = CrashReportStore.read(this)
        if (previousCrash != null) {
            showCrashReport(previousCrash)
        } else {
            showSafeScreen()
        }
    }

    private fun showSafeScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(48), dp(28), dp(28))
            setBackgroundColor(Color.rgb(2, 4, 8))
        }

        root.addView(TextView(this).apply {
            text = "GALAXY TAB DIAGNOSTIC"
            setTextColor(Color.rgb(255, 239, 188))
            textSize = 13f
            gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "별을 읽는 성역"
            setTextColor(Color.rgb(246, 226, 177))
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(8))
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "1단계 안전 화면\n\n이 화면이 유지되면 앱 기본 실행 계층은 정상입니다.\n아래 버튼을 눌러 마법진 렌더러만 따로 실행하세요."
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.35f)
            setPadding(0, dp(24), 0, dp(32))
        }, matchWrap())

        root.addView(Button(this).apply {
            text = "마법진 렌더러 테스트"
            setOnClickListener {
                CrashReportStore.clear(this@MainActivity)
                runMagicRenderer()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(58)
        ))

        root.addView(TextView(this).apply {
            text = "버전 2.1-diagnostic\n서비스 · 알림 · BootReceiver 미사용"
            setTextColor(Color.rgb(160, 170, 180))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
        }, matchWrap())

        setContentView(root)
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
            text = "로그 지우고 안전 화면으로 돌아가기"
            setOnClickListener {
                CrashReportStore.clear(this@MainActivity)
                showSafeScreen()
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
