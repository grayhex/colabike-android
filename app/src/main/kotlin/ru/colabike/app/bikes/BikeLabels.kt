package ru.colabike.app.bikes

import ru.colabike.core.model.BikeClassification

/**
 * The words of the site's bike classification ("cola lib/bike-classification.ts"), keyed by the
 * API's own keys. A key this version does not know shows nothing instead of a guess: the site adds
 * values before the app learns them.
 */
object BikeLabels {
    /** The families a list can be filtered by, in the order the site shows them. */
    val categories: List<Pair<String, String>> =
        listOf(
            "mtb" to "MTB",
            "road_gravel" to "Шоссе / гравел",
            "urban_touring" to "Город / туризм",
            "bmx" to "BMX",
            "cargo_utility" to "Грузовой / утилитарный",
            "special" to "Специальный",
        )

    private val categoryNames = categories.toMap() + mapOf("road" to "Шоссе", "gravel" to "Гравел")

    private val subtypes =
        mapOf(
            "xc" to "XC",
            "trail" to "Trail",
            "enduro" to "Enduro",
            "downhill" to "Downhill",
            "dirt_jump" to "Dirt Jump",
            "road" to "Road",
            "endurance" to "Endurance",
            "aero" to "Aero",
            "gravel" to "Gravel",
            "cyclocross" to "Cyclocross",
            "tt_triathlon" to "TT / Triathlon",
            "track" to "Track",
            "commuter" to "Commuter",
            "fitness_hybrid" to "Fitness / Hybrid",
            "trekking" to "Trekking",
            "touring" to "Touring",
            "cruiser" to "Cruiser",
            "bmx_race" to "Race",
            "freestyle" to "Freestyle",
            "street_park" to "Street / Park",
            "cargo" to "Cargo",
            "longtail" to "Longtail",
            "utility" to "Utility",
            "tandem" to "Tandem",
            "recumbent" to "Recumbent",
            "adaptive" to "Adaptive",
            "other" to "Other",
        )

    private val suspensions =
        mapOf("rigid" to "Rigid", "hardtail" to "Hardtail", "full_suspension" to "Full Suspension")

    private val constructions =
        mapOf(
            "standard" to "Standard",
            "folding" to "Folding",
            "cargo" to "Cargo",
            "tandem" to "Tandem",
            "recumbent" to "Recumbent",
        )

    private val uses =
        mapOf(
            "racing" to "Racing",
            "xc" to "XC",
            "trail" to "Trail",
            "enduro" to "Enduro",
            "downhill" to "Downhill",
            "gravel" to "Gravel",
            "cyclocross" to "Cyclocross",
            "road_racing" to "Road Racing",
            "audax_endurance" to "Audax / Endurance",
            "commuting" to "Commuting",
            "touring" to "Touring",
            "bikepacking" to "Bikepacking",
            "cargo" to "Cargo",
            "leisure" to "Leisure",
        )

    fun category(key: String): String? = categoryNames[key]

    /** The words of the editor's choices; a key the app does not know shows as itself. */
    fun subtype(key: String): String = subtypes[key] ?: key

    fun suspension(key: String): String = suspensions[key] ?: key

    fun construction(key: String): String = constructions[key] ?: key

    fun use(key: String): String = uses[key] ?: key

    /**
     * The few facts that say what the bike is, at most three, as the site's badges do: the family
     * with its subtype ("MTB · Trail"), then the suspension, a construction other than the usual
     * one, "E-bike" and "Fatbike". Unknown keys are left out.
     */
    fun badges(classification: BikeClassification): List<String> {
        val family =
            listOfNotNull(
                    categoryNames[classification.category],
                    classification.subtype?.let(subtypes::get),
                )
                .joinToString(" · ")
                .takeIf { it.isNotEmpty() }
        return listOfNotNull(
                family,
                classification.suspension?.let(suspensions::get),
                classification.construction?.takeIf { it != "standard" }?.let(constructions::get),
                "E-bike".takeIf { classification.electric },
                "Fatbike".takeIf { classification.fatbike },
            )
            .take(MAX_BADGES)
    }

    private const val MAX_BADGES = 3
}
