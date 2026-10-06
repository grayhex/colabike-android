package ru.colabike.core.model

/** Which prices of a bike its owner shows to other people; with none given, all are hidden. */
data class PriceVisibility(
    val bike: Boolean = false,
    val components: Boolean = false,
    val accessories: Boolean = false,
)

/**
 * What the owner says the bike is, as the API takes it: independent facets. A key is the API's own;
 * [BikeCatalog] lists the ones the server accepts.
 */
data class ClassificationDraft(
    val category: String,
    val subtype: String? = null,
    val suspension: String? = null,
    val construction: String? = null,
    val uses: List<String> = emptyList(),
    val electric: Boolean = false,
    val fatbike: Boolean = false,
)

/**
 * A bike as the owner's form holds it and the server takes it. [year] is null only for a bike the
 * server holds without one: a new bike needs it ([BikeRules.check]). Texts are as typed; the server
 * trims them, and an empty text is "nothing", which is how a text is taken away.
 */
data class BikeDraft(
    val name: String,
    val brand: String,
    val model: String,
    val trim: String,
    val year: Int?,
    val classification: ClassificationDraft,
    val description: String,
    val color: String,
    val size: String,
    val weightKg: Double?,
    val mileageKm: Int,
    val manufacturerUrl: String,
    val priceRub: Double?,
    val priceVisibility: PriceVisibility,
    val isFormer: Boolean,
    val isPublic: Boolean,
)

/**
 * What changed between the bike the owner read and the form: only the named fields go to the server
 * (`PATCH`), so that an edit never writes back what it did not touch. A weight or a price that was
 * taken away is [clearWeight] / [clearPrice], because `null` is a value of its own there.
 */
data class BikePatch(
    val name: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val trim: String? = null,
    val year: Int? = null,
    val classification: ClassificationDraft? = null,
    val description: String? = null,
    val color: String? = null,
    val size: String? = null,
    val weightKg: Double? = null,
    val clearWeight: Boolean = false,
    val mileageKm: Int? = null,
    val manufacturerUrl: String? = null,
    val priceRub: Double? = null,
    val clearPrice: Boolean = false,
    val priceVisibility: PriceVisibility? = null,
    val isFormer: Boolean? = null,
    val isPublic: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = this == BikePatch()

    /** The bike becomes visible to everyone, which needs a confirmed address. */
    val publishes: Boolean
        get() = isPublic == true
}

/** The fields that differ from [original]; an empty patch means nothing needs to be sent. */
fun BikeDraft.diff(original: BikeDraft): BikePatch =
    BikePatch(
        name = name.takeIf { it != original.name },
        brand = brand.takeIf { it != original.brand },
        model = model.takeIf { it != original.model },
        trim = trim.takeIf { it != original.trim },
        year = year.takeIf { it != original.year },
        classification = classification.takeIf { it != original.classification },
        description = description.takeIf { it != original.description },
        color = color.takeIf { it != original.color },
        size = size.takeIf { it != original.size },
        weightKg = weightKg.takeIf { it != original.weightKg },
        clearWeight = weightKg == null && original.weightKg != null,
        mileageKm = mileageKm.takeIf { it != original.mileageKm },
        manufacturerUrl = manufacturerUrl.takeIf { it != original.manufacturerUrl },
        priceRub = priceRub.takeIf { it != original.priceRub },
        clearPrice = priceRub == null && original.priceRub != null,
        priceVisibility = priceVisibility.takeIf { it != original.priceVisibility },
        isFormer = isFormer.takeIf { it != original.isFormer },
        isPublic = isPublic.takeIf { it != original.isPublic },
    )

/** The bike as the form starts from it when its owner changes it. */
fun BikeDetail.toDraft(): BikeDraft =
    BikeDraft(
        name = summary.name,
        brand = summary.brand,
        model = summary.model,
        trim = trim,
        year = summary.year,
        classification =
            ClassificationDraft(
                category = summary.classification.category,
                subtype = summary.classification.subtype,
                suspension = summary.classification.suspension,
                construction = summary.classification.construction,
                uses = summary.classification.uses,
                electric = summary.classification.electric,
                fatbike = summary.classification.fatbike,
            ),
        description = description,
        color = color,
        size = size,
        weightKg = weightKg,
        mileageKm = mileageKm,
        manufacturerUrl = manufacturerUrl.orEmpty(),
        priceRub = priceRub,
        priceVisibility = priceVisibility ?: PriceVisibility(),
        isFormer = summary.isFormer,
        isPublic = summary.isPublic,
    )

/** The classification the server accepts, by its own keys (cola `lib/bike-classification.ts`). */
object BikeCatalog {
    val categories: List<String> =
        listOf("mtb", "road_gravel", "urban_touring", "bmx", "cargo_utility", "special")

    /** The subtypes of each category; a subtype of another category is refused. */
    val subtypes: Map<String, List<String>> =
        mapOf(
            "mtb" to listOf("xc", "trail", "enduro", "downhill", "dirt_jump"),
            "road_gravel" to
                listOf(
                    "road",
                    "endurance",
                    "aero",
                    "gravel",
                    "cyclocross",
                    "tt_triathlon",
                    "track",
                ),
            "urban_touring" to
                listOf("commuter", "fitness_hybrid", "trekking", "touring", "cruiser"),
            "bmx" to listOf("bmx_race", "freestyle", "street_park"),
            "cargo_utility" to listOf("cargo", "longtail", "utility"),
            "special" to listOf("tandem", "recumbent", "adaptive", "other"),
        )

    val suspensions: List<String> = listOf("rigid", "hardtail", "full_suspension")

    val constructions: List<String> = listOf("standard", "folding", "cargo", "tandem", "recumbent")

    val uses: List<String> =
        listOf(
            "racing",
            "xc",
            "trail",
            "enduro",
            "downhill",
            "gravel",
            "cyclocross",
            "road_racing",
            "audax_endurance",
            "commuting",
            "touring",
            "bikepacking",
            "cargo",
            "leisure",
        )

    const val MAX_USES = 3
}

/** What is wrong with a bike form, in the server's own terms; checked before a request is made. */
sealed interface BikeProblem {
    data object NoName : BikeProblem

    data object NameTooLong : BikeProblem

    data object BrandTooLong : BikeProblem

    data object ModelTooLong : BikeProblem

    data object TrimTooLong : BikeProblem

    /** A new bike has a model year. */
    data object NoYear : BikeProblem

    data object YearOutOfRange : BikeProblem

    data object NoCategory : BikeProblem

    data object DescriptionTooLong : BikeProblem

    data object ColorTooLong : BikeProblem

    data object SizeTooLong : BikeProblem

    /** Not a number, or not above zero and at most 100 kg. */
    data object WeightInvalid : BikeProblem

    data object MileageInvalid : BikeProblem

    /** Not an `https` address (the app shows only those), or too long. */
    data object LinkInvalid : BikeProblem

    data object PriceInvalid : BikeProblem
}

/** The limits of the server for one bike (cola `bikeWriteShape`). */
object BikeRules {
    const val MAX_NAME = 100
    const val MAX_BRAND = 60
    const val MAX_MODEL = 100
    const val MAX_TRIM = 100
    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2100
    const val MAX_DESCRIPTION = 2000
    const val MAX_COLOR = 60
    const val MAX_SIZE = 30
    const val MAX_WEIGHT_KG = 100.0
    const val MAX_MILEAGE_KM = 10_000_000
    const val MAX_PRICE_RUB = 999_999_999.0
    const val MAX_LINK = 2048

    /**
     * Everything the form can get wrong that the server would refuse. [needsYear]: a new bike has
     * its year, and so does one that had it (a year cannot be taken away, the server has no way to
     * say so); only a bike the server holds without one may stay so. The numbers of a form that did
     * not parse are told apart by the caller, which has the text; here they are already numbers.
     */
    fun check(draft: BikeDraft, needsYear: Boolean): List<BikeProblem> = buildList {
        if (draft.name.trim().isEmpty()) add(BikeProblem.NoName)
        if (draft.name.trim().length > MAX_NAME) add(BikeProblem.NameTooLong)
        if (draft.brand.trim().length > MAX_BRAND) add(BikeProblem.BrandTooLong)
        if (draft.model.trim().length > MAX_MODEL) add(BikeProblem.ModelTooLong)
        if (draft.trim.trim().length > MAX_TRIM) add(BikeProblem.TrimTooLong)
        val year = draft.year
        if (year == null) {
            if (needsYear) add(BikeProblem.NoYear)
        } else if (year !in MIN_YEAR..MAX_YEAR) {
            add(BikeProblem.YearOutOfRange)
        }
        if (draft.classification.category.isEmpty()) add(BikeProblem.NoCategory)
        if (draft.description.trim().length > MAX_DESCRIPTION) add(BikeProblem.DescriptionTooLong)
        if (draft.color.trim().length > MAX_COLOR) add(BikeProblem.ColorTooLong)
        if (draft.size.trim().length > MAX_SIZE) add(BikeProblem.SizeTooLong)
        draft.weightKg?.let {
            if (!(it > 0.0 && it <= MAX_WEIGHT_KG)) add(BikeProblem.WeightInvalid)
        }
        if (draft.mileageKm !in 0..MAX_MILEAGE_KM) add(BikeProblem.MileageInvalid)
        if (!isLink(draft.manufacturerUrl)) add(BikeProblem.LinkInvalid)
        draft.priceRub?.let {
            if (!(it >= 0.0 && it <= MAX_PRICE_RUB)) add(BikeProblem.PriceInvalid)
        }
    }

    /** Empty, or a plain `https` address without credentials, as the app shows links. */
    fun isLink(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty()) return true
        if (text.length > MAX_LINK) return false
        if (!text.startsWith("https://", ignoreCase = true)) return false
        val rest = text.substring("https://".length)
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.isNotEmpty() && '@' !in host && !host.any { it.isWhitespace() }
    }
}

/**
 * A bike that was saved or removed here, for the screens that show it to agree without a reload.
 */
sealed interface BikeChange {
    data class Saved(val bike: BikeDetail) : BikeChange

    data class Removed(val id: BikeId) : BikeChange
}
