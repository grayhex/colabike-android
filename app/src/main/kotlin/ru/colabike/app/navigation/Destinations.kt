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

    @Serializable data class Bike(val id: String) : Destination

    @Serializable data object Rides : Destination

    @Serializable data object Messages : Destination

    @Serializable data object Profile : Destination

    /** A person's public page; [ref] is a UUID (from a list) or a username (from a link). */
    @Serializable data class Person(val ref: String) : Destination

    /** The people who follow [ref], or whom [ref] follows. */
    @Serializable data class People(val ref: String, val following: Boolean) : Destination

    /** A journal entry; [id] is its UUID. */
    @Serializable data class Journal(val id: String) : Destination

    /** The journal of one bike. */
    @Serializable data class BikeJournal(val bikeId: String, val bikeName: String) : Destination

    /** The entries the signed-in person saved, opened from the profile. */
    @Serializable data object SavedJournal : Destination

    /** Search over builds and people; [people] opens it on the people tab. */
    @Serializable data class Search(val people: Boolean = false) : Destination

    /** Where the account is signed in; opened from the profile. */
    @Serializable data object Devices : Destination

    @Serializable data object About : Destination

    @Serializable data object Licenses : Destination
}
