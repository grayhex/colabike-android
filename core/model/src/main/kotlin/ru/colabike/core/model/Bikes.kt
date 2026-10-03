package ru.colabike.core.model

/** A picture the app can load. [url] is absolute: the network layer resolves site paths. */
data class Photo(val id: String, val url: String)

/** A bike as a list shows it. */
data class BikeSummary(
    val id: BikeId,
    val name: String,
    val brand: String,
    val model: String,
    /** Model year, or null when the owner did not give one. */
    val year: Int?,
    val category: String,
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
    val description: String,
    val color: String,
    val size: String,
    val weightKg: Double?,
    val mileageKm: Int,
    val photos: List<Photo>,
    val components: List<BikeComponent>,
)

data class BikeComponent(
    val id: String,
    val section: String,
    val category: String,
    val name: String,
    val notes: String,
)

/** Which bikes a list asks for: everyone's public bikes or the viewer's own, private included. */
enum class BikeScope {
    Public,
    Mine,
}
