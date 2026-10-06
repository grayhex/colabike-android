package ru.colabike.core.network

import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.colabike.api.apis.AccountApi
import ru.colabike.api.apis.BikesApi
import ru.colabike.api.apis.SafetyApi
import ru.colabike.api.apis.SearchApi
import ru.colabike.api.apis.SessionsApi
import ru.colabike.api.apis.UsersApi
import ru.colabike.api.models.CreateReportRequest
import ru.colabike.api.models.DeleteAccountRequest
import ru.colabike.api.models.DeleteAccountRequestReauth
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountDeletion
import ru.colabike.core.model.AccountDeletionRepository
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.AccountSession
import ru.colabike.core.model.AccountSessionsRepository
import ru.colabike.core.model.BikeChange
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeDraft
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikePatch
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikeSearch
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.BlockChange
import ru.colabike.core.model.DataError
import ru.colabike.core.model.DeletionProof
import ru.colabike.core.model.FollowChange
import ru.colabike.core.model.FollowState
import ru.colabike.core.model.LikeChange
import ru.colabike.core.model.LikeState
import ru.colabike.core.model.Page
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.model.PersonSummary
import ru.colabike.core.model.Profile
import ru.colabike.core.model.ReportKind
import ru.colabike.core.model.ReportReason
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.SafetyRepository
import ru.colabike.core.model.UserId

class NetworkBikesRepository(
    private val api: BikesApi,
    private val searchApi: SearchApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** The bikes API whose `PATCH` body also names these fields as `null` (a weight, a price). */
    private val clearing: (nulls: Set<String>) -> BikesApi = { api },
) : BikesRepository {
    private val likes = MutableSharedFlow<LikeChange>(extraBufferCapacity = 16)
    override val likeChanges: SharedFlow<LikeChange> = likes.asSharedFlow()
    private val saved = MutableSharedFlow<BikeChange>(extraBufferCapacity = 16)
    override val changes: SharedFlow<BikeChange> = saved.asSharedFlow()

    override suspend fun bikes(query: BikeQuery, cursor: String?, limit: Int): Page<BikeSummary> =
        apiCall(dispatcher) {
                api.listBikes(
                    scope =
                        when (query.scope) {
                            BikeScope.Public -> BikesApi.ScopeListBikes.`public`
                            BikeScope.Mine -> BikesApi.ScopeListBikes.mine
                        },
                    // Categories are OR-ed by the server; the order is fixed for stable requests.
                    category =
                        query.categories.sorted().joinToString(",").takeIf { it.isNotEmpty() },
                    q = query.text.trim().take(MAX_QUERY).takeIf { it.isNotEmpty() },
                    limit = limit,
                    cursor = cursor,
                )
            }
            .toModel(media)

    override suspend fun search(
        search: BikeSearch,
        cursor: String?,
        limit: Int,
    ): Page<BikeSummary> =
        apiCall(dispatcher) {
                searchApi.searchExperienceBikes(
                    q = search.text.trim().take(MAX_QUERY).takeIf { it.isNotEmpty() },
                    category = search.category?.let(::searchCategory),
                    suspension = search.suspension?.let(::searchSuspension),
                    electric = search.electric?.let { if (it) ELECTRIC_ON else ELECTRIC_OFF },
                    fatbike = search.fatbike?.let { if (it) FATBIKE_ON else FATBIKE_OFF },
                    limit = limit,
                    cursor = cursor,
                )
            }
            .toModel(media)

    override suspend fun bike(id: BikeId): BikeDetail {
        // A malformed id cannot name a bike; the API would answer 404 as well.
        val uuid =
            runCatching { UUID.fromString(id.value) }.getOrNull() ?: throw DataError.NotFound()
        // The owner's answer carries the version an edit names in `If-Match`.
        return apiCall(dispatcher) { api.getBikeWithHttpInfo(uuid).valueAndTag() }
            .let { (dto, tag) -> dto.toModel(media, tag) }
    }

    override suspend fun create(draft: BikeDraft, key: String): BikeDetail {
        // The key is a UUID by contract; a bad one is a bug here, not a request to send.
        require(runCatching { UUID.fromString(key) }.isSuccess) { "Idempotency-Key must be a UUID" }
        val request = draft.toRequest()
        return apiCall(dispatcher) {
                api.createBikeWithHttpInfo(UUID.fromString(key), request).valueAndTag()
            }
            .let { (dto, tag) -> dto.toModel(media, tag) }
            .also { saved.tryEmit(BikeChange.Saved(it)) }
    }

    override suspend fun update(id: BikeId, patch: BikePatch, version: String?): BikeDetail {
        val uuid =
            runCatching { UUID.fromString(id.value) }.getOrNull() ?: throw DataError.NotFound()
        // An edit applies to the version that was read; without one the server would answer 428.
        val tag = version ?: throw DataError.Rejected(428, "precondition_required", "")
        if (patch.isEmpty) return bike(id)
        val nulls = buildSet {
            if (patch.clearWeight) add("weight")
            if (patch.clearPrice) add("price")
        }
        val request = patch.toRequest()
        return apiCall(dispatcher) {
                clearing(nulls).updateBikeWithHttpInfo(uuid, tag, request).valueAndTag()
            }
            .let { (dto, newTag) -> dto.toModel(media, newTag) }
            .also { saved.tryEmit(BikeChange.Saved(it)) }
    }

    override suspend fun delete(id: BikeId) {
        val uuid =
            runCatching { UUID.fromString(id.value) }.getOrNull() ?: throw DataError.NotFound()
        try {
            apiCall(dispatcher) { api.deleteBike(uuid) }
        } catch (_: DataError.NotFound) {
            // Deleted elsewhere first: what was asked for has happened, and the lists that still
            // show the bike are told as well.
        }
        saved.tryEmit(BikeChange.Removed(id))
    }

    override suspend fun setLiked(id: BikeId, liked: Boolean): LikeState {
        val uuid =
            runCatching { UUID.fromString(id.value) }.getOrNull() ?: throw DataError.NotFound()
        val answer = apiCall(dispatcher) { if (liked) api.likeBike(uuid) else api.unlikeBike(uuid) }
        return LikeState(liked = answer.liked, likes = answer.likes).also {
            likes.tryEmit(LikeChange(id, it))
        }
    }

    // A value the API does not list is not sent at all (the server would answer 400).
    private fun searchCategory(key: String) =
        SearchApi.CategorySearchExperienceBikes.entries.firstOrNull {
            it.value == key &&
                it != SearchApi.CategorySearchExperienceBikes.unknown_default_open_api
        }

    private fun searchSuspension(key: String) =
        SearchApi.SuspensionSearchExperienceBikes.entries.firstOrNull {
            it.value == key &&
                it != SearchApi.SuspensionSearchExperienceBikes.unknown_default_open_api
        }

    private companion object {
        /** The API's own limit on `q`. */
        const val MAX_QUERY = 150
        val ELECTRIC_ON = SearchApi.ElectricSearchExperienceBikes._1
        val ELECTRIC_OFF = SearchApi.ElectricSearchExperienceBikes._0
        val FATBIKE_ON = SearchApi.FatbikeSearchExperienceBikes._1
        val FATBIKE_OFF = SearchApi.FatbikeSearchExperienceBikes._0
    }
}

class NetworkAccountRepository(
    private val api: AccountApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountRepository {
    override suspend fun me(): Account = apiCall(dispatcher) { api.getMe() }.toAccount(media)
}

/**
 * Reporting and blocking (cola docs/modules/api-v1.md, "Безопасность: блокировки и жалобы"). [api]
 * is the client with the Bearer interceptor.
 */
class NetworkSafetyRepository(
    private val api: SafetyApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SafetyRepository {
    private val changes = MutableSharedFlow<BlockChange>(extraBufferCapacity = 16)
    override val blockChanges: SharedFlow<BlockChange> = changes.asSharedFlow()

    override suspend fun report(target: ReportTarget, reason: ReportReason): Boolean {
        // A malformed id cannot name an object; the API would answer 404 as well.
        val id =
            runCatching { UUID.fromString(target.id) }.getOrNull() ?: throw DataError.NotFound()
        val request =
            CreateReportRequest(
                entityType =
                    when (target.kind) {
                        ReportKind.Profile -> CreateReportRequest.EntityType.profile
                        ReportKind.Bike -> CreateReportRequest.EntityType.bike
                        ReportKind.BikeComment -> CreateReportRequest.EntityType.comment
                        ReportKind.Ride -> CreateReportRequest.EntityType.ride
                        ReportKind.RideComment -> CreateReportRequest.EntityType.ride_comment
                        ReportKind.Journal -> CreateReportRequest.EntityType.journal
                        ReportKind.JournalComment -> CreateReportRequest.EntityType.journal_comment
                        ReportKind.ComponentComment ->
                            CreateReportRequest.EntityType.component_comment
                        ReportKind.ComponentPhoto -> CreateReportRequest.EntityType.component_photo
                    },
                targetId = id,
                reason =
                    when (reason) {
                        ReportReason.Spam -> CreateReportRequest.Reason.spam
                        ReportReason.Abuse -> CreateReportRequest.Reason.abuse
                        ReportReason.Inappropriate -> CreateReportRequest.Reason.inappropriate
                        ReportReason.Copyright -> CreateReportRequest.Reason.copyright
                        ReportReason.Other -> CreateReportRequest.Reason.other
                    },
            )
        return apiCall(dispatcher) { api.createReport(request) }.created
    }

    override suspend fun setBlocked(id: UserId, blocked: Boolean): Boolean {
        val uuid =
            runCatching { UUID.fromString(id.value) }.getOrNull() ?: throw DataError.NotFound()
        val result =
            apiCall(dispatcher) {
                if (blocked) api.blockUser(uuid.toString()) else api.unblockUser(uuid.toString())
            }
        changes.tryEmit(BlockChange(id, result.blocked))
        return result.blocked
    }

    override suspend fun blocked(cursor: String?, limit: Int): Page<PersonSummary> =
        apiCall(dispatcher) { api.listBlockedUsers(limit.coerceIn(1, 50), cursor) }.toModel(media)
}

/**
 * Deleting the account (cola docs/modules/api-v1.md, "Аккаунт: удаление"). [api] is the client with
 * the Bearer interceptor. The confirmation word is the server's contract, not a screen's choice.
 */
class NetworkAccountDeletionRepository(
    private val api: AccountApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountDeletionRepository {
    override suspend fun deletion(): AccountDeletion =
        apiCall(dispatcher) { api.getAccountDeletion() }.toModel()

    override suspend fun delete(proof: DeletionProof) {
        val request =
            when (proof) {
                is DeletionProof.Password ->
                    DeleteAccountRequest(confirm = CONFIRMATION_WORD, password = proof.value)
                is DeletionProof.Provider ->
                    DeleteAccountRequest(
                        confirm = CONFIRMATION_WORD,
                        reauth =
                            DeleteAccountRequestReauth(
                                code = proof.code,
                                codeVerifier = proof.verifier,
                            ),
                    )
            }
        apiCall(dispatcher) { api.deleteAccount(request) }
    }

    private companion object {
        const val CONFIRMATION_WORD = "УДАЛИТЬ"
    }
}

/**
 * The account's sessions. [api] must be the client with the Bearer interceptor: these calls are
 * made as the signed-in person.
 */
class NetworkAccountSessionsRepository(
    private val api: SessionsApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountSessionsRepository {
    override suspend fun sessions(): List<AccountSession> =
        apiCall(dispatcher) { api.listSessions() }.items.map { it.toModel() }

    override suspend fun revoke(id: String) {
        // A malformed id cannot name a session; the API would answer 404 as well.
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: throw DataError.NotFound()
        apiCall(dispatcher) { api.revokeSession(uuid) }
    }
}

/**
 * People for the screens. [ref] is a UUID or a username; anything else cannot name a person, so it
 * is "not found" without a request.
 */
class NetworkPeopleRepository(
    private val users: UsersApi,
    private val searchApi: SearchApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PeopleRepository {
    private val changes = MutableSharedFlow<FollowChange>(extraBufferCapacity = 16)
    override val followChanges: SharedFlow<FollowChange> = changes.asSharedFlow()

    override suspend fun profile(ref: String): Profile =
        apiCall(dispatcher) { users.getUser(checked(ref)) }.toModel(media)

    override suspend fun bikesOf(ref: String, cursor: String?, limit: Int): Page<BikeSummary> =
        apiCall(dispatcher) { users.listUserBikes(checked(ref), limit, cursor) }.toModel(media)

    override suspend fun followers(ref: String, cursor: String?, limit: Int): Page<PersonSummary> =
        apiCall(dispatcher) { users.listFollowers(checked(ref), limit, cursor) }.toModel(media)

    override suspend fun following(ref: String, cursor: String?, limit: Int): Page<PersonSummary> =
        apiCall(dispatcher) { users.listFollowing(checked(ref), limit, cursor) }.toModel(media)

    override suspend fun search(text: String, cursor: String?, limit: Int): Page<PersonSummary> {
        val q = text.trim().take(MAX_QUERY)
        // The API requires a text; no text is no people, not "everyone".
        if (q.isEmpty()) return Page(emptyList(), null)
        return apiCall(dispatcher) { searchApi.searchExperienceUsers(q, limit, cursor) }
            .toModel(media)
    }

    override suspend fun setFollowing(id: UserId, following: Boolean): FollowState {
        val ref = checked(id.value)
        val answer =
            apiCall(dispatcher) {
                if (following) users.followUser(ref) else users.unfollowUser(ref)
            }
        return answer.toModel().also { changes.tryEmit(FollowChange(id, it)) }
    }

    private fun checked(ref: String): String {
        val valid = UUID_REF.matches(ref) || USERNAME_REF.matches(ref)
        if (!valid) throw DataError.NotFound()
        return ref
    }

    private companion object {
        const val MAX_QUERY = 150
        val UUID_REF =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** `{ref}`: a username of 3 to 30 characters. */
        val USERNAME_REF = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{2,29}$")
    }
}
