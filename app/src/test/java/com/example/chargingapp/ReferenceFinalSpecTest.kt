package com.example.chargingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceFinalSpecTest {

    @Test
    fun referenceFinalUsesDenseButReadableRings() {
        assertEquals(48, ReferenceFinalSpec.outerRuneCount)
        assertEquals(56, ReferenceFinalSpec.innerRuneCount)
        assertEquals(4, ReferenceFinalSpec.orbitCount)
        assertEquals(8, ReferenceFinalSpec.primaryOrbitNodeCount)
        assertTrue(ReferenceFinalSpec.coreRadiusScale >= 0.25f)
    }

    @Test
    fun onlyRuneLayersAnimate() {
        assertTrue(AnimationLayerPolicy.animateOuterRuneBand)
        assertTrue(AnimationLayerPolicy.animateInnerRuneBand)
        assertFalse(AnimationLayerPolicy.animateSacredGeometry)
        assertFalse(AnimationLayerPolicy.animateOrbitCurvesAndNodes)
        assertFalse(AnimationLayerPolicy.animateCenterCore)
    }
}
