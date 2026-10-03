package ru.colabike.app.people

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.FollowState
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.model.Profile

@Immutable
data class PersonUiState(
    val profile: Profile? = null,
    val loading: Boolean = true,
    /**
     * The page could not be shown at all (the person is hidden, blocked, unknown, or no network).
     */
    val error: UiText? = null,
    /**
     * Private, blocked and unknown people are the same answer; a guest may be the one who sees it.
     */
    val notFound: Boolean = false,
    val bikes: List<BikeSummary> = emptyList(),
    val nextCursor: String? = null,
    val bikesLoading: Boolean = false,
    val bikesError: UiText? = null,
    /** A subscription is on its way to the server: the button already shows the new state. */
    val following: Boolean = false,
    val followError: UiText? = null,
)

/**
 * A person's public page: the profile, their public bikes page by page, and the subscription. [ref]
 * is whatever opened the page (a UUID from a list, a username from a link); once the profile is
 * loaded the UUID in it is the key to everything else.
 */
class PersonViewModel(
    private val people: PeopleRepository,
    private val bikesRepository: BikesRepository,
    private val ref: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PersonUiState())
    val state: StateFlow<PersonUiState> = mutableState.asStateFlow()

    init {
        load()
        // A subscription made elsewhere (a list) shows here without loading the page again.
        viewModelScope.launch {
            people.followChanges.collect { change ->
                mutableState.update { current ->
                    if (current.profile?.person?.id == change.id) current.withFollow(change.state)
                    else current
                }
            }
        }
        // A like given on a bike's page shows on the card here.
        viewModelScope.launch {
            bikesRepository.likeChanges.collect { change ->
                mutableState.update { current ->
                    current.copy(
                        bikes =
                            current.bikes.map {
                                if (it.id == change.id)
                                    it.copy(liked = change.state.liked, likes = change.state.likes)
                                else it
                            }
                    )
                }
            }
        }
    }

    fun load() {
        mutableState.value = PersonUiState()
        viewModelScope.launch {
            try {
                val profile = people.profile(ref)
                mutableState.update { it.copy(profile = profile, loading = false) }
                loadBikes(first = true)
            } catch (e: DataError) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        error = e.toUiText(),
                        notFound = e is DataError.NotFound,
                    )
                }
            }
        }
    }

    fun loadMoreBikes() = loadBikes(first = false)

    private fun loadBikes(first: Boolean) {
        val current = state.value
        val id = current.profile?.person?.id?.value ?: return
        if (current.bikesLoading || (!first && current.nextCursor == null)) return
        mutableState.update { it.copy(bikesLoading = true, bikesError = null) }
        viewModelScope.launch {
            try {
                val page = people.bikesOf(id, cursor = if (first) null else current.nextCursor)
                mutableState.update {
                    it.copy(
                        bikes =
                            (if (first) page.items else it.bikes + page.items).distinctBy { b ->
                                b.id
                            },
                        nextCursor = page.nextCursor,
                        bikesLoading = false,
                    )
                }
            } catch (e: DataError) {
                // The profile stays; only the list of bikes offers its own retry.
                mutableState.update { it.copy(bikesLoading = false, bikesError = e.toUiText()) }
            }
        }
    }

    /**
     * Follows or unfollows at once and lets the server have the last word, as a like does: its
     * answer sets the button and the count, a refusal puts the old ones back and says why. Not for
     * oneself, which the server does not allow.
     */
    fun toggleFollow() {
        val current = state.value
        val profile = current.profile ?: return
        val relationship = profile.relationship ?: return
        if (current.following || relationship.isSelf) return
        val before = FollowState(relationship, profile.counts.followers)
        val target = !relationship.following
        mutableState.value =
            current
                .withFollow(
                    FollowState(
                        relationship.copy(
                            following = target,
                            friends = target && relationship.followedBy,
                        ),
                        (profile.counts.followers + if (target) 1 else -1).coerceAtLeast(0),
                    )
                )
                .copy(following = true, followError = null)
        viewModelScope.launch {
            try {
                val answer = people.setFollowing(profile.person.id, target)
                mutableState.update { it.withFollow(answer).copy(following = false) }
            } catch (e: DataError) {
                mutableState.update {
                    it.withFollow(before).copy(following = false, followError = e.toUiText())
                }
            }
        }
    }

    private fun PersonUiState.withFollow(follow: FollowState): PersonUiState =
        profile?.let {
            copy(
                profile =
                    it.copy(
                        relationship = follow.relationship,
                        counts = it.counts.copy(followers = follow.followers),
                    )
            )
        } ?: this
}
