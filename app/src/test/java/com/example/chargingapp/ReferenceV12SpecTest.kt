package com.example.chargingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceV12SpecTest {

    @Test
    fun usesApprovedVirtualCanvasAndReferenceLayerCounts() {
        assertEquals(1080f, ReferenceV12Spec.virtualWidth, 0.001f)
        assertEquals(2400f, ReferenceV12Spec.virtualHeight, 0.001f)
        assertEquals(4, ReferenceV12Spec.galaxyClusterCount)
        assertEquals(48, ReferenceV12Spec.outerRuneCount)
        assertEquals(56, ReferenceV12Spec.innerRuneCount)
        assertEquals(4, ReferenceV12Spec.orbitCount)
        assertEquals(8, ReferenceV12Spec.primaryOrbitNodeCount)
    }

    @Test
    fun keepsLargeCoreAndStaticLayersSixSevenEight() {
        assertTrue(ReferenceV12Spec.coreRadius >= 235f)
        assertFalse(AnimationLayerPolicy.animateSacredGeometry)
        assertFalse(AnimationLayerPolicy.animateOrbitCurvesAndNodes)
        assertFalse(AnimationLayerPolicy.animateCenterCore)
    }
}
