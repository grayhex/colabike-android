package ru.colabike.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 keys. Serializable, so the back stacks survive process death; arguments are ids,
 * never objects or secrets (a screen loads what it shows). A top-level section is the first key of
 * its own stack.
 */
@Serializable
sealed interface Destination : NavKey {
    @Serializable data object Feed : Destination

    @Serializable data object Bikes : Destination

    /** The component catalog, opened from the Bikes section. */
    @Serializable data object Components : Destination

    /** One model of the catalog; [id] is its UUID (a merged model shows its canonical one). */
    @Serializable data class Component(val id: String) : Destination

    /** The market list; [seller] is a `username` when the list is one person's listings. */
    @Serializable data class Market(val seller: String? = null) : Destination

    /** A market listing; [id] is its UUID. */
    @Serializable data class Listing(val id: String) : Destination

    /** The listings the signed-in person saved, opened from the profile. */
    @Serializable data object SavedMarket : Destination

    @Serializable data class Bike(val id: String) : Destination

    @Serializable data object Rides : Destination

    /** A ride or a plan; [id] is its UUID. */
    @Serializable data class Ride(val id: String) : Destination

    /** The completed public rides of one bike. */
    @Serializable data class BikeRides(val bikeId: String, val bikeName: String) : Destination

    @Serializable data object Messages : Destination

    /** One conversation; [cid] is the chat provider's `type:id` of the channel. */
    @Serializable data class Conversation(val cid: String) : Destination

    /** Who to write to: the people one follows or finds, a dialogue or a group. */
    @Serializable data object NewConversation : Destination

    /** The inbox of ColaBike events; opened from the bell of a top-level screen. */
    @Serializable data object Notifications : Destination

    @Serializable data object Profile : Destination

    /** A person's public page; [ref] is a UUID (from a list) or a username (from a link). */
    @Serializable data class Person(val ref: String) : Destination

    /** The people who follow [ref], or whom [ref] follows. */
    @Serializable data class People(val ref: String, val following: Boolean) : Destination

    /** A journal entry; [id] is its UUID. */
    @Serializable data class Journal(val id: String) : Destination

    /** The journal of one bike. */
    @Serializable data class BikeJournal(val bikeId: String, val bikeName: String) : Destination

    /**
     * The discussion under a bike or a journal entry. [kind] is `bike` or `journal` (rides and
     * component models use the same screen later), [title] names the object in the top bar, and
     * [focus] is a comment to open the discussion at (a link, a notification).
     */
    @Serializable
    data class Comments(
        val kind: String,
        val id: String,
        val title: String,
        val focus: String? = null,
    ) : Destination

    /** The entries the signed-in person saved, opened from the profile. */
    @Serializable data object SavedJournal : Destination

    /** Search over builds and people; [people] opens it on the people tab. */
    @Serializable data class Search(val people: Boolean = false) : Destination

    /** Where the account is signed in; opened from the profile. */
    @Serializable data object Devices : Destination

    @Serializable data object About : Destination

    @Serializable data object Licenses : Destination
}
