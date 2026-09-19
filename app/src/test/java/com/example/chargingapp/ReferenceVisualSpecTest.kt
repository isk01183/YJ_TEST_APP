package com.example.chargingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceVisualSpecTest {

    @Test
    fun referenceMatchUsesLargeReadableRuneBands() {
        assertEquals(40, ReferenceVisualSpec.outerMajorRuneCount)
        assertEquals(48, ReferenceVisualSpec.innerBlueRuneCount)
        assertTrue(ReferenceVisualSpec.outerRuneRadius > ReferenceVisualSpec.innerRuneRadius)
    }

    @Test
    fun referenceMatchUsesBrightGalaxyAndLargeCore() {
        assertEquals(4, ReferenceVisualSpec.galaxyClusterCount)
        assertTrue(ReferenceVisualSpec.coreRadiusScale >= 0.27f)
        assertTrue(ReferenceVisualSpec.outerGoldGlowStrength > 0.90f)
        assertTrue(ReferenceVisualSpec.innerBlueGlowStrength > 0.85f)
    }

    @Test
    fun staticLayersSixSevenEightRemainStatic() {
        assertFalse(AnimationLayerPolicy.animateSacredGeometry)
        assertFalse(AnimationLayerPolicy.animateOrbitCurvesAndNodes)
        assertFalse(AnimationLayerPolicy.animateCenterCore)
    }
}
