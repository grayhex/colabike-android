package ru.colabike.core.designsystem.component

import java.time.Instant
import java.time.LocalDate
import ru.colabike.core.model.BikeClassification
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.JournalSummary
import ru.colabike.core.model.ListingBrief
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
            classification =
                BikeClassification(
                    category = "urban_touring",
                    subtype = "touring",
                    suspension = null,
                    construction = null,
                    electric = false,
                    fatbike = false,
                ),
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

    /** A full bike page: photos, a passport, a price the owner shows, the build in two groups. */
    val bikeDetail =
        BikeDetail(
            summary =
                bike.copy(
                    classification =
                        BikeClassification(
                            category = "mtb",
                            subtype = "trail",
                            suspension = "full_suspension",
                            construction = null,
                            electric = false,
                            fatbike = false,
                        ),
                    photoCount = 3,
                ),
            trim = "Pro",
            description = "Надёжный горный велосипед для поездок круглый год.",
            color = "Графит",
            size = "L",
            weightKg = 14.2,
            mileageKm = 1200,
            manufacturerUrl = "https://www.cube.eu/travel-sl",
            purposes = listOf("trail"),
            priceRub = 85_000.0,
            groupOrder = listOf("drivetrain", "frame"),
            photos =
                listOf(
                    Photo("p1", "https://example.test/photo-1.jpg"),
                    Photo("p2", "https://example.test/photo-2.jpg"),
                    Photo("p3", "https://example.test/photo-3.jpg"),
                ),
            components =
                listOf(
                    BikeComponent(
                        "c1",
                        "build",
                        "Рама",
                        "Cube Aluminium Superlite",
                        "Алюминий, 17.5\"",
                        groupId = "frame",
                        sortOrder = 0,
                    ),
                    BikeComponent(
                        "c2",
                        "build",
                        "Трансмиссия",
                        "Shimano Deore 10-speed",
                        "Кассета 11-42",
                        url = "https://example.test/deore",
                        groupId = "drivetrain",
                        sortOrder = 1,
                        priceRub = 12_500.0,
                    ),
                    BikeComponent(
                        "c3",
                        "build",
                        "Трансмиссия",
                        "Shimano Deore, цепь",
                        "",
                        groupId = "drivetrain",
                        sortOrder = 2,
                    ),
                    BikeComponent("c4", "accessories", "Звонок", "Латунный", ""),
                ),
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

    val journal =
        JournalSummary(
            id = JournalId("j1"),
            kind = "service",
            title = "Замена цепи и кассеты после тысячи километров",
            status = JournalStatus.Published,
            isPublic = true,
            eventDate = LocalDate.parse("2026-09-14"),
            mileageKm = 4_200,
            createdAt = Instant.parse("2026-09-14T18:30:00Z"),
            updatedAt = Instant.parse("2026-09-15T08:00:00Z"),
            bike = BikeRef(BikeId("b1"), "Городской Трэвел"),
            author = rider,
            likes = 5,
            comments = 2,
            liked = false,
            excerpt = "Цепь вытянулась на 0,75 %, поставил новую и заодно сменил кассету на 11-42.",
        )

    val listing =
        ListingBrief(
            id = "l1",
            title = "Втулка Shimano Deore, почти новая",
            price = 2_500.0,
            currency = "RUB",
            category = "components",
            type = "sale",
            location = "Москва",
            cover = null,
            author = rider,
        )
}
