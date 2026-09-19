package com.example.chargingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationMathTest {

    @Test
    fun wrapDegrees_normalizesPositiveAndNegativeAngles() {
        assertEquals(10f, AnimationMath.wrapDegrees(370f), 0.0001f)
        assertEquals(350f, AnimationMath.wrapDegrees(-10f), 0.0001f)
    }

    @Test
    fun pulse01_staysInsideZeroToOne() {
        for (i in 0..1000) {
            val t = i / 60f
            val value = AnimationMath.pulse01(t, 0.8f, 0.17f)
            assertTrue(value in 0f..1f)
        }
    }

    @Test
    fun damp_movesTowardTargetWithoutOvershoot() {
        val start = 20f
        val target = 80f
        val next = AnimationMath.damp(start, target, 1f / 60f, 7f)

        assertTrue(next > start)
        assertTrue(next < target)
        assertEquals(start, AnimationMath.damp(start, target, 0f, 7f), 0.0001f)
    }
}
