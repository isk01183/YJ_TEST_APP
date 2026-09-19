package com.example.chargingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceV13SpecTest {

    @Test
    fun matchesReferenceComposition() {
        assertEquals(1080f, ReferenceV13Spec.virtualWidth, 0.001f)
        assertEquals(2400f, ReferenceV13Spec.virtualHeight, 0.001f)
        assertEquals(4, ReferenceV13Spec.galaxyClusterCount)
        assertEquals(44, ReferenceV13Spec.outerRuneCount)
        assertEquals(52, ReferenceV13Spec.innerRuneCount)
        assertEquals(4, ReferenceV13Spec.orbitCount)
        assertEquals(8, ReferenceV13Spec.primaryOrbitNodeCount)
        assertTrue(ReferenceV13Spec.magicRadius >= 520f)
        assertTrue(ReferenceV13Spec.coreRadius >= 245f)
    }

    @Test
    fun onlyRuneBandsAnimate() {
        assertTrue(AnimationLayerPolicy.animateOuterRuneBand)
        assertTrue(AnimationLayerPolicy.animateInnerRuneBand)
        assertFalse(AnimationLayerPolicy.animateSacredGeometry)
        assertFalse(AnimationLayerPolicy.animateOrbitCurvesAndNodes)
        assertFalse(AnimationLayerPolicy.animateCenterCore)
    }
}
