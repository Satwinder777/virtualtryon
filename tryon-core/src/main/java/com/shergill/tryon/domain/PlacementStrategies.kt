package com.shergill.tryon.domain

object PlacementStrategies {
    fun forType(type: AccessoryType): PlacementStrategy = when (type) {
        AccessoryType.CAP -> CapPlacementStrategy()
        AccessoryType.GLASSES -> GlassesPlacementStrategy()
        AccessoryType.EARRINGS -> EarringsPlacementStrategy()
        AccessoryType.LOCKET -> LocketPlacementStrategy()
    }
}
