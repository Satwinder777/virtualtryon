package com.shergill.tryon.ui

import com.shergill.tryon.domain.AccessoryType

/**
 * Curated sample GLB URLs verified to start with the binary `glTF` magic header.
 * Prefer raw.githubusercontent.com / Khronos sample assets for stable hosting.
 */
data class SampleGlbModel(
    val label: String,
    val url: String,
    val suggestedType: AccessoryType,
)

object SampleGlbModels {
    val ALL: List<SampleGlbModel> = listOf(
        SampleGlbModel(
            label = "Sunglasses (Khronos)",
            url = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/SunglassesKhronos/glTF-Binary/SunglassesKhronos.glb",
            suggestedType = AccessoryType.GLASSES,
        ),
        SampleGlbModel(
            label = "Glasses — Round",
            url = "https://raw.githubusercontent.com/nirholas/three.ws/main/public/accessories/glasses-round.glb",
            suggestedType = AccessoryType.GLASSES,
        ),
        SampleGlbModel(
            label = "Glasses — Shades",
            url = "https://raw.githubusercontent.com/nirholas/three.ws/main/public/accessories/glasses-shades.glb",
            suggestedType = AccessoryType.GLASSES,
        ),
        SampleGlbModel(
            label = "Hat — Baseball Cap",
            url = "https://raw.githubusercontent.com/nirholas/three.ws/main/public/accessories/hat-baseball.glb",
            suggestedType = AccessoryType.CAP,
        ),
        SampleGlbModel(
            label = "Hat — Beanie",
            url = "https://raw.githubusercontent.com/nirholas/three.ws/main/public/accessories/hat-beanie.glb",
            suggestedType = AccessoryType.CAP,
        ),
        SampleGlbModel(
            label = "Hat — Cowboy",
            url = "https://raw.githubusercontent.com/nirholas/three.ws/main/public/accessories/hat-cowboy.glb",
            suggestedType = AccessoryType.CAP,
        ),
        SampleGlbModel(
            label = "Earrings — Hoops",
            url = "https://raw.githubusercontent.com/nirholas/three.ws/main/public/accessories/earrings-hoops.glb",
            suggestedType = AccessoryType.EARRINGS,
        ),
        SampleGlbModel(
            label = "Earrings — Studs",
            url = "https://raw.githubusercontent.com/nirholas/three.ws/main/public/accessories/earrings-studs.glb",
            suggestedType = AccessoryType.EARRINGS,
        ),
        SampleGlbModel(
            label = "Damaged Helmet (Khronos)",
            url = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/DamagedHelmet/glTF-Binary/DamagedHelmet.glb",
            suggestedType = AccessoryType.CAP,
        ),
        SampleGlbModel(
            label = "Avocado (small — Locket test)",
            url = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/Avocado/glTF-Binary/Avocado.glb",
            suggestedType = AccessoryType.LOCKET,
        ),
        SampleGlbModel(
            label = "Duck (small — Locket test)",
            url = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/Duck/glTF-Binary/Duck.glb",
            suggestedType = AccessoryType.LOCKET,
        ),
        SampleGlbModel(
            label = "Water Bottle (Khronos)",
            url = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/WaterBottle/glTF-Binary/WaterBottle.glb",
            suggestedType = AccessoryType.LOCKET,
        ),
        SampleGlbModel(
            label = "Boom Box (Khronos)",
            url = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/BoomBox/glTF-Binary/BoomBox.glb",
            suggestedType = AccessoryType.LOCKET,
        ),
        SampleGlbModel(
            label = "Toy Car (Khronos)",
            url = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/ToyCar/glTF-Binary/ToyCar.glb",
            suggestedType = AccessoryType.CAP,
        ),
        SampleGlbModel(
            label = "Fox (Khronos)",
            url = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/Fox/glTF-Binary/Fox.glb",
            suggestedType = AccessoryType.CAP,
        ),
    )

    val DEFAULT: SampleGlbModel = ALL.first()
}
