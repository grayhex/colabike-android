package ru.colabike.core.designsystem.component

import java.time.Instant
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.Person
import ru.colabike.core.model.Photo
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RideSummary
import ru.colabike.core.model.UserId

/** Made-up data for previews and screenshot tests; never real people. */
object PreviewData {
    val rider = Person(UserId("u1"), "test-rider", "Тестовый Райдер", avatarUrl = null)

    val bike =
        BikeSummary(
            id = BikeId("b1"),
            name = "Городской Трэвел",
            brand = "Cube",
            model = "Travel SL",
            year = 2020,
            category = "urban_touring",
            cover = Photo("p1", "https://example.test/photo.jpg"),
            photoCount = 3,
            author = rider,
            likes = 4,
            liked = true,
            comments = 21,
            isOwner = false,
            isPublic = true,
            isFormer = false,
        )

    val bikeWithoutPhoto =
        bike.copy(
            id = BikeId("b2"),
            name = "Очень длинное название велосипеда, которое не помещается в одну строку",
            cover = null,
            isFormer = true,
            liked = false,
            author = null,
            year = null,
        )

    val ride =
        RideSummary(
            id = RideId("r1"),
            title = "Вечерняя по набережной",
            status = RideStatus.Completed,
            time = Instant.parse("2026-09-20T16:00:00Z"),
            distanceMeters = 32_450,
            movingTimeSeconds = 5_400,
            bikeName = "Городской Трэвел",
            author = rider,
        )

    val plannedRide =
        ride.copy(
            id = RideId("r2"),
            title = "Воскресный выезд за город",
            status = RideStatus.Planned,
            distanceMeters = null,
            movingTimeSeconds = null,
        )
}
