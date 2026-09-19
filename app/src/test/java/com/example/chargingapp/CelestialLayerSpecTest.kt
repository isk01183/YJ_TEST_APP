package com.example.chargingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelestialLayerSpecTest {

    @Test
    fun layer6UsesDenseStaticSacredGeometry() {
        assertFalse(AnimationLayerPolicy.animateSacredGeometry)
        assertTrue(CelestialLayerSpec.sacredPolygonSides.containsAll(listOf(6, 8, 12, 16)))
        assertEquals(32, CelestialLayerSpec.sacredRayCount)
        assertEquals(24, CelestialLayerSpec.sacredNodeCount)
        assertEquals(3, CelestialLayerSpec.guideEllipseRotations.size)
    }

    @Test
    fun layer7UsesFourStaticOrbitsWithPrimaryAndSecondaryNodes() {
        assertFalse(AnimationLayerPolicy.animateOrbitCurvesAndNodes)
        assertEquals(4, CelestialLayerSpec.orbitRotations.size)
        assertEquals(8, CelestialLayerSpec.primaryOrbitNodeAngles.size)
        assertEquals(8, CelestialLayerSpec.secondaryOrbitNodeAngles.size)
        assertEquals(listOf(0f, 45f, 90f, 135f), CelestialLayerSpec.orbitRotations.toList())
    }
}
