package com.example.chargingapp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object AnimationMath {

    fun wrapDegrees(value: Float): Float {
        val wrapped = value % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    fun pulse01(timeSeconds: Float, frequencyHz: Float, phaseCycles: Float = 0f): Float {
        if (frequencyHz <= 0f) return 0.5f
        val radians = 2.0 * PI * (frequencyHz * timeSeconds + phaseCycles)
        return (0.5f + 0.5f * sin(radians).toFloat()).coerceIn(0f, 1f)
    }

    fun damp(current: Float, target: Float, deltaSeconds: Float, response: Float): Float {
        if (deltaSeconds <= 0f || response <= 0f || current == target) return current
        val blend = (1f - exp((-response * deltaSeconds).toDouble()).toFloat()).coerceIn(0f, 1f)
        return current + (target - current) * blend
    }
}
