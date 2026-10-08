package ru.colabike.app

import java.time.Instant
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.rides.map.SketchRouteMaps
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.Page
import ru.colabike.core.model.Person
import ru.colabike.core.model.Photo
import ru.colabike.core.model.RideRoute
import ru.colabike.core.model.UserId

/** Repeatable acceptance content, also used for before/after comparisons. */
fun realisticDependencies(
    visualRoute: RideRoute = VisualRoutes.load("river"),
    maps: RouteMaps = SketchRouteMaps,
    photoFiles: FakePhotoFiles = FakePhotoFiles(),
): FakeDependencies {
    val author = Person(UserId("visual-author"), "sasha.rides", "Александра Соколова", null)
    val blue =
        PreviewData.bike.copy(
            id = BikeId("visual-blue"),
            name = "Ocean Blue",
            brand = "Mikamaro",
            model = "Ocean Blue",
            year = 2024,
            cover = Photo("blue", "https://example.test/gravel-blue.jpg"),
            author = author,
            likes = 128,
            comments = 12,
            liked = false,
        )
    val dark =
        blue.copy(
            id = BikeId("visual-dark"),
            name = "Гравий, лес и длинная дорога домой",
            brand = "ARC8",
            model = "Eero",
            cover = Photo("dark", "https://example.test/gravel-dark.jpg"),
            likes = 9,
        )
    val green =
        blue.copy(
            id = BikeId("visual-green"),
            name = "По делам и за хлебом",
            brand = "Bianchi",
            model = "City",
            cover = Photo("green", "https://example.test/bike-green.jpg"),
            likes = 0,
        )
    val bike =
        PreviewData.bikeDetail.copy(
            summary = blue,
            description =
                "В будни — вдоль набережной. В выходные — за город, на гравий и тихие лесные дороги.",
            photos =
                listOf(blue.cover!!, Photo("portrait", "https://example.test/bike-portrait.jpg")),
            trim = "",
            manufacturerUrl = null,
            components = emptyList(),
        )
    val ride =
        rideDetail(0).let {
            it.copy(
                summary =
                    it.summary.copy(
                        title = "Вдоль реки к утреннему кофе",
                        author = author,
                    ),
                route = visualRoute,
                description =
                    "Набережная, тихие улицы и остановка у любимой кофейни. Отличное начало воскресенья.",
            )
        }
    val viewer =
        account.copy(
            name = "Михаил Орлов",
            username = "misha.velo",
            bio = "Гравий, небольшие города и кофе в дороге. Ищу компанию на воскресенье.",
            emailVerified = true,
        )
    val myAuthor = Person(viewer.id, viewer.username, viewer.name, viewer.avatarUrl)
    val myBlue = blue.copy(id = BikeId("visual-own-blue"), author = myAuthor, isOwner = true)
    val myDark = dark.copy(id = BikeId("visual-own-dark"), author = myAuthor, isOwner = true)
    val journal =
        PreviewData.journal.copy(
            title = "Выходные выше облаков",
            kind = "story",
            excerpt =
                "Подъём к озеру, прохладный ветер и дорога, ради которой стоит проснуться пораньше.",
            bike = BikeRef(blue.id, blue.name),
            author = author,
        )
    val now = Instant.parse("2026-10-03T14:00:00Z")
    return FakeDependencies(
        maps = maps,
        photoFiles = photoFiles,
        bikes =
            FakeBikes(mapOf(null to Page(listOf(blue, dark, green), null))).apply {
                details =
                    mapOf(
                        blue.id.value to bike,
                        myBlue.id.value to bike.copy(summary = myBlue),
                        myDark.id.value to
                            bike.copy(summary = myDark, photos = listOf(myDark.cover!!)),
                    )
            },
        rides =
            FakeRides(
                completedPages = mapOf(null to Page(listOf(ride.summary), null)),
                details = mapOf(ride.summary.id.value to ride),
            ),
        feed =
            FakeFeed(
                mapOf(
                    null to
                        Page(
                            listOf(
                                FeedItem.Ride(ride.summary, now),
                                FeedItem.Journal(journal, now),
                                FeedItem.Bike(blue, now),
                            ),
                            null,
                        )
                )
            ),
        intents =
            FakeIntents(
                community =
                    listOf(
                        sampleIntent(1).copy(author = author),
                        sampleIntent(2, area = "Лосиный остров")
                            .copy(
                                author =
                                    author.copy(id = UserId("visual-igor"), name = "Игорь Лебедев")
                            ),
                    )
            ),
        journal = FakeJournal(pages = mapOf(null to Page(listOf(journal), null))),
        people = FakePeople(bikesOfPerson = Page(listOf(myBlue, myDark), null)),
        auth = FakeAuth(AuthState.SignedIn(viewer)),
        account = FakeAccount { viewer },
        notifications = FakeNotifications(unread = NotificationCount(128, capped = true)),
    )
}
