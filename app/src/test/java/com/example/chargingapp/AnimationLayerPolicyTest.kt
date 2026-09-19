package com.example.chargingapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationLayerPolicyTest {

    @Test
    fun runeBandsRemainAnimated() {
        assertTrue(AnimationLayerPolicy.animateOuterRuneBand)
        assertTrue(AnimationLayerPolicy.animateInnerRuneBand)
    }

    @Test
    fun layersSixSevenEightRemainStatic() {
        assertFalse(AnimationLayerPolicy.animateSacredGeometry)
        assertFalse(AnimationLayerPolicy.animateOrbitCurvesAndNodes)
        assertFalse(AnimationLayerPolicy.animateCenterCore)
    }
}
