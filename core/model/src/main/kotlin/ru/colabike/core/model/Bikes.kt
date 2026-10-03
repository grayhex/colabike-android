package ru.colabike.core.model

/** A picture the app can load. [url] is absolute: the network layer resolves site paths. */
data class Photo(val id: String, val url: String)

/**
 * What kind of bike it is: independent facets, not a tree (the site's own model). Keys are the
 * API's; the app turns the ones it knows into words and shows nothing for the ones it does not.
 */
data class BikeClassification(
    val category: String,
    val subtype: String?,
    val suspension: String?,
    val construction: String?,
    val electric: Boolean,
    val fatbike: Boolean,
)

/** A bike as a list shows it. */
data class BikeSummary(
    val id: BikeId,
    val name: String,
    val brand: String,
    val model: String,
    /** Model year, or null when the owner did not give one. */
    val year: Int?,
    val category: String,
    val classification: BikeClassification,
    val cover: Photo?,
    val photoCount: Int,
    val author: Person?,
    val likes: Int,
    val liked: Boolean,
    val comments: Int,
    val isOwner: Boolean,
    val isPublic: Boolean,
    val isFormer: Boolean,
)

/** The bike page: the summary plus what only the detail shows. */
data class BikeDetail(
    val summary: BikeSummary,
    /** The version of the model ("Pro", "SL"), empty when there is none. */
    val trim: String,
    val description: String,
    val color: String,
    val size: String,
    val weightKg: Double?,
    val mileageKm: Int,
    /** The maker's page for the bike, or null; only an `https` address is kept. */
    val manufacturerUrl: String?,
    /** Free-form purposes the owner named. */
    val purposes: List<String>,
    /** The price in roubles when the viewer may see it: the owner's own, or one the owner shows. */
    val priceRub: Double?,
    /** The owner's order of component groups ([BikeComponent.groupId]); empty means by section. */
    val groupOrder: List<String>,
    val photos: List<Photo>,
    val components: List<BikeComponent>,
)

data class BikeComponent(
    val id: String,
    val section: String,
    val category: String,
    val name: String,
    val notes: String,
    /** The maker's page for the component, or null; only an `https` address is kept. */
    val url: String? = null,
    val groupId: String = "",
    val sortOrder: Int = 0,
    /** In roubles when the viewer may see it. */
    val priceRub: Double? = null,
)

/** The state of a like after a `PUT` or `DELETE`: what the server says, not what we hoped. */
data class LikeState(val liked: Boolean, val likes: Int)

/** A like that changed, for every screen showing the same bike to agree without a reload. */
data class LikeChange(val id: BikeId, val state: LikeState)

/**
 * What a bike list asks for: [scope], free text (name, brand, model, author) and categories (a bike
 * matches any of them). Blank text and no categories mean "everything in the scope".
 */
data class BikeQuery(
    val scope: BikeScope = BikeScope.Public,
    val text: String = "",
    val categories: Set<String> = emptySet(),
) {
    val isFiltered: Boolean
        get() = text.isNotBlank() || categories.isNotEmpty()
}

/** Which bikes a list asks for: everyone's public bikes or the viewer's own, private included. */
enum class BikeScope {
    Public,
    Mine,
}
