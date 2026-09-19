package com.example.chargingapp

object CelestialLayerSpec {
    val sacredPolygonSides = intArrayOf(6, 8, 12, 16)
    const val sacredRayCount = 32
    const val sacredNodeCount = 24
    val guideEllipseRotations = floatArrayOf(0f, 60f, 120f)

    val orbitRotations = floatArrayOf(0f, 45f, 90f, 135f)
    val primaryOrbitNodeAngles = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
    val secondaryOrbitNodeAngles = floatArrayOf(22.5f, 67.5f, 112.5f, 157.5f, 202.5f, 247.5f, 292.5f, 337.5f)
}
