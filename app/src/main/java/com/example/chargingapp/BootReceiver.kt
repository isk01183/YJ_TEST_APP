package com.example.chargingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 기기가 재부팅되거나(휴대폰을 껐다 켜도) 앱이 업데이트된 직후에도
 * 인터넷 연결 없이 자동으로 ChargingForegroundService 를 다시 시작시켜서
 * "설치되면 항상 작동" 요구사항을 만족시킵니다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                ChargingForegroundService.start(context)
            }
        }
    }
}
