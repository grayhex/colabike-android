package ru.colabike.core.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.BikeChange
import ru.colabike.core.model.BikeDraft
import ru.colabike.core.model.BuildQuery
import ru.colabike.core.model.ClassificationDraft
import ru.colabike.core.model.ComponentDraft
import ru.colabike.core.model.DataError
import ru.colabike.core.model.PriceVisibility
import ru.colabike.core.model.ResolutionStatus
import ru.colabike.core.model.ResolveRequest
import ru.colabike.core.model.SourceKind

class BikeWizardRepositoryTest {
    private val site = TestServer()
    private val bikes =
        NetworkBikesRepository(
            site.api.bikes,
            site.api.search,
            site.media,
            // A real thread for the blocking call, as in the app: a stopped search can stop it.
            Dispatchers.IO,
            resolving = site.api::bikesResolving,
        )
    private val wizard = bikes.wizard
    private val key = "0c7d9a1e-5b2f-4c3d-8e4a-1f2b3c4d5e6f"
    private val preview = "5c1c1b6e-9d6e-4a5b-8a33-6f1d2a9b0c11"
    private val query = BuildQuery("Giant", "Contend", "AR 1", 2024)

    @After fun close() = site.close()

    private fun body(request: mockwebserver3.RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    private val draft =
        BikeDraft(
            name = "",
            brand = "Giant",
            model = "Contend",
            trim = "AR 1",
            year = 2024,
            classification = ClassificationDraft(category = "road_gravel", subtype = "road"),
            description = "",
            color = "",
            size = "",
            weightKg = null,
            mileageKm = 750,
            manufacturerUrl = "",
            priceRub = null,
            priceVisibility = PriceVisibility(),
            isFormer = false,
            isPublic = false,
        )
    private val saddle =
        ComponentDraft("build", " Седло ", " Fizik Antares ", "", 5000.0, "", "cockpit")

    // --- asking ------------------------------------------------------------------------------

    @Test
    fun `a search with nothing else named asks for the variants`() = runTest {
        site.json(200, site.fixture("bike-resolution-ambiguous.json"))

        wizard.resolve(ResolveRequest.Search(query))

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bike-resolutions")
        val sent = body(request)
        assertThat(sent["brand"]?.jsonPrimitive?.content).isEqualTo("Giant")
        assertThat(sent["model"]?.jsonPrimitive?.content).isEqualTo("Contend")
        assertThat(sent["trim"]?.jsonPrimitive?.content).isEqualTo("AR 1")
        assertThat(sent["year"]?.jsonPrimitive?.content).isEqualTo("2024")
        assertThat(sent["chooseCandidates"]?.jsonPrimitive?.boolean).isTrue()
        assertThat(sent.containsKey("candidateId")).isFalse()
        assertThat(sent.containsKey("sourceUrl")).isFalse()
    }

    @Test
    fun `a variant is chosen by the identifier the server gave, and asks for nothing else`() =
        runTest {
            site.json(200, site.fixture("bike-resolution-resolved.json"))

            wizard.resolve(ResolveRequest.Variant(query, "b".repeat(64)))

            val sent = body(site.server.takeRequest())
            assertThat(sent["candidateId"]?.jsonPrimitive?.content).isEqualTo("b".repeat(64))
            assertThat(sent.containsKey("chooseCandidates")).isFalse()
            assertThat(sent.containsKey("sourceUrl")).isFalse()
        }

    @Test
    fun `a page that was pasted is sent as it is and asks for nothing else`() = runTest {
        site.json(200, site.fixture("bike-resolution-resolved.json"))

        wizard.resolve(
            ResolveRequest.Page(BuildQuery("Giant", "Contend"), " https://shop.example/bike ")
        )

        val sent = body(site.server.takeRequest())
        assertThat(sent["sourceUrl"]?.jsonPrimitive?.content).isEqualTo("https://shop.example/bike")
        assertThat(sent.containsKey("chooseCandidates")).isFalse()
        assertThat(sent.containsKey("trim")).isFalse()
        assertThat(sent.containsKey("year")).isFalse()
    }

    // --- what came of it ------------------------------------------------------------------------

    @Test
    fun `a found build is the draft of its parts, the source and what to confirm`() = runTest {
        site.json(200, site.fixture("bike-resolution-resolved.json"))

        val result = wizard.resolve(ResolveRequest.Search(query))

        assertThat(result.status).isEqualTo(ResolutionStatus.Resolved)
        assertThat(result.previewId).isEqualTo(preview)
        assertThat(result.previewExpiresAt).isEqualTo(Instant.parse("2026-10-07T17:00:00Z"))
        val build = result.build!!
        assertThat(build.name).isEqualTo("Giant Contend AR 1")
        assertThat(build.sourceKind).isEqualTo(SourceKind.Manufacturer)
        assertThat(build.sourceHost).isEqualTo("www.giant.example")
        assertThat(build.identityMismatch).isTrue()
        assertThat(build.yearMismatch).isTrue()
        assertThat(build.needsConfirmation).isTrue()
        assertThat(build.quality?.complete).isFalse()
        assertThat(build.parts.map { it.category })
            .containsExactly("Рама", "Вилка", "Седло", "Звонок")
            .inOrder()
        assertThat(build.parts.map { it.section })
            .containsExactly("build", "build", "build", "accessories")
            .inOrder()
        assertThat(build.parts.map { it.groupId })
            .containsExactly("frame", "frame", "cockpit", "equipment")
            .inOrder()
        assertThat(build.unrecognized.single().label).isEqualTo("Прочее")
        assertThat(build.suggested.weightKg).isEqualTo(9.8)
        assertThat(build.suggested.color).isEqualTo("Black")
        assertThat(build.suggested.wheelSize).isNull()
    }

    @Test
    fun `several variants are a list to choose from, each with its own source and warnings`() =
        runTest {
            site.json(200, site.fixture("bike-resolution-ambiguous.json"))

            val result =
                wizard.resolve(ResolveRequest.Search(BuildQuery("Focus", "Atlas 6.7 Cues")))

            assertThat(result.status).isEqualTo(ResolutionStatus.Ambiguous)
            assertThat(result.build).isNull()
            assertThat(result.previewId).isNull()
            val (store, other) = result.candidates
            assertThat(store.candidateId).isEqualTo("b".repeat(64))
            assertThat(store.sourceKind).isEqualTo(SourceKind.Store)
            assertThat(store.sourceName).isEqualTo("Bikeinn")
            assertThat(store.year).isNull()
            assertThat(store.drivetrain).isEqualTo("Shimano GRX 2x11")
            assertThat(store.quality?.complete).isTrue()
            assertThat(store.quality?.recognizedComponents).isEqualTo(18)
            assertThat(store.warnings).containsExactly("multiple_builds")
            assertThat(store.otherHosts).containsExactly("a.example")
            assertThat(store.selectable).isTrue()
            assertThat(other.candidateId).isNull()
            assertThat(other.year).isEqualTo(2023)
            assertThat(other.selectable).isFalse()
            assertThat(result.sourcesChecked?.asked).isEqualTo(2)
            assertThat(result.sourcesChecked?.answered).isEqualTo(1)
            assertThat(result.sourcesChecked?.complete).isFalse()
        }

    @Test
    fun `a service that did not answer is an answer with a reason, not an error`() = runTest {
        site.json(200, site.fixture("bike-resolution-unavailable.json"))

        val result = wizard.resolve(ResolveRequest.Search(query))

        assertThat(result.status).isEqualTo(ResolutionStatus.Unavailable)
        assertThat(result.retryable).isTrue()
        assertThat(result.reason).isEqualTo("timeout")
        assertThat(result.candidates).isEmpty()
    }

    @Test
    fun `too many searches is a wait the server names`() = runTest {
        site.json(429, error("rate_limited"), "Retry-After", "42")

        val failure = runCatching { wizard.resolve(ResolveRequest.Search(query)) }.exceptionOrNull()

        assertThat((failure as DataError.RateLimited).retryAfterSeconds).isEqualTo(42)
    }

    @Test
    fun `a search that is stopped stops the connection instead of waiting for the answer`() =
        runBlocking {
            site.server.enqueue(
                MockResponse.Builder().code(200).headersDelay(30, TimeUnit.SECONDS).build()
            )
            val search =
                launch(Dispatchers.Default, CoroutineStart.UNDISPATCHED) {
                    wizard.resolve(ResolveRequest.Search(query))
                }
            assertThat(site.server.takeRequest(10, TimeUnit.SECONDS)).isNotNull()
            val before = System.nanoTime()

            search.cancelAndJoin()

            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)
            assertThat(tookMs).isLessThan(5_000)
        }

    // --- making --------------------------------------------------------------------------------

    @Test
    fun `a bike is made with the checked parts, the preview and the agreement, under a key`() =
        runTest {
            site.json(201, site.fixture("bike.json"), "ETag", "\"bike-v1\"")

            bikes.changes.test {
                val made =
                    wizard.create(
                        draft,
                        listOf(saddle),
                        preview,
                        identityConfirmed = true,
                        key = key,
                    )

                assertThat(made.version).isEqualTo("\"bike-v1\"")
                assertThat(awaitItem()).isEqualTo(BikeChange.Saved(made))
            }
            val request = site.server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/api/v1/bike-wizard/bikes")
            assertThat(request.headers["Idempotency-Key"]).isEqualTo(key)
            val sent = body(request)
            assertThat(sent["previewId"]?.jsonPrimitive?.content).isEqualTo(preview)
            assertThat(sent["identityConfirmed"]?.jsonPrimitive?.boolean).isTrue()
            val bike = sent["bike"]!!.jsonObject
            assertThat(bike["brand"]?.jsonPrimitive?.content).isEqualTo("Giant")
            assertThat(bike["year"]?.jsonPrimitive?.content).isEqualTo("2024")
            assertThat(bike["isPublic"]?.jsonPrimitive?.boolean).isFalse()
            assertThat(bike["mileage"]?.jsonPrimitive?.content).isEqualTo("750")
            // No name was typed: the server names the bike, the app does not guess for it.
            assertThat(bike.containsKey("name")).isFalse()
            val parts: JsonArray = sent["components"]!!.jsonArray
            val part = parts.single().jsonObject
            assertThat(part["section"]?.jsonPrimitive?.content).isEqualTo("build")
            assertThat(part["category"]?.jsonPrimitive?.content).isEqualTo("Седло")
            assertThat(part["name"]?.jsonPrimitive?.content).isEqualTo("Fizik Antares")
            assertThat(part["price"]?.jsonPrimitive?.content).isEqualTo("5000.0")
            assertThat(part["groupId"]?.jsonPrimitive?.content).isEqualTo("cockpit")
        }

    @Test
    fun `a bike by hand has no preview and says nothing of agreement`() = runTest {
        site.json(201, site.fixture("bike.json"), "ETag", "\"bike-v1\"")

        wizard.create(
            draft.copy(name = "Мой"),
            emptyList(),
            null,
            identityConfirmed = false,
            key = key,
        )

        val sent = body(site.server.takeRequest())
        assertThat(sent.containsKey("previewId")).isFalse()
        assertThat(sent.containsKey("identityConfirmed")).isFalse()
        assertThat(sent["bike"]!!.jsonObject["name"]?.jsonPrimitive?.content).isEqualTo("Мой")
        assertThat(sent["components"]!!.jsonArray).isEmpty()
        assertThat(sent["bike"]!!.jsonObject.containsKey("trim")).isTrue()
        assertThat(JsonPrimitive(1).content).isEqualTo("1")
    }

    @Test
    fun `a preview that is gone and a difference not agreed to are told apart by the field`() =
        runTest {
            site.json(
                409,
                """{"error":{"code":"conflict","message":"Результат поиска устарел.","details":[{"path":"previewId","message":"Предпросмотр устарел или не найден."}]}}""",
            )
            site.json(
                409,
                """{"error":{"code":"conflict","message":"Подтвердите отличие.","details":[{"path":"identityConfirmed","message":"Нужно подтверждение."}]}}""",
            )

            val gone = runCatching {
                wizard.create(draft, emptyList(), preview, false, key)
            }
                .exceptionOrNull()
            val different = runCatching {
                wizard.create(draft, emptyList(), preview, false, key)
            }
                .exceptionOrNull()

            assertThat((gone as DataError.Rejected).field).isEqualTo("previewId")
            assertThat(gone.status).isEqualTo(409)
            assertThat(gone.userMessage).isEqualTo("Результат поиска устарел.")
            assertThat((different as DataError.Rejected).field).isEqualTo("identityConfirmed")
        }

    @Test
    fun `a refusal that names no field has none`() = runTest {
        site.json(403, error("email_verification_required", "Подтвердите почту."))

        val failure = runCatching {
            wizard.create(draft, emptyList(), null, false, key)
        }
            .exceptionOrNull()

        assertThat((failure as DataError.Rejected).field).isNull()
        assertThat(failure.code).isEqualTo("email_verification_required")
    }
}
