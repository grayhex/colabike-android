package ru.colabike.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.ColaIcons

/**
 * The target top-level sections of the app: Feed, Bikes, Rides, Messages, Profile. A section is
 * [available] only when the slice that implements it exists: a release never shows a tab that leads
 * to an empty stub. Turn a flag on in the same change that adds the screen (the slice named next to
 * it), and its tab, its back stack and its reselect behaviour appear with no other edit.
 *
 * Search and notifications are not sections but actions on a screen's top bar, and "create" is the
 * contextual action of the screen it creates for; they arrive with their slices the same way.
 */
enum class TopLevel(
    val root: Destination,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val available: Boolean,
) {
    /** Following feed, journal, saved: slice 6. */
    Feed(Destination.Feed, R.string.nav_feed, ColaIcons.Feed, ColaIcons.FeedFilled, true),
    Bikes(Destination.Bikes, R.string.nav_bikes, ColaIcons.Bike, ColaIcons.BikeFilled, true),

    /** Rides and plans, one's own (the map: second part of slice 7). */
    Rides(Destination.Rides, R.string.nav_rides, ColaIcons.Route, ColaIcons.Route, true),

    /** Personal chat (the provider's SDK in the app's look): slice 8. */
    Messages(
        Destination.Messages,
        R.string.nav_messages,
        ColaIcons.Chat,
        ColaIcons.ChatFilled,
        true,
    ),
    Profile(
        Destination.Profile,
        R.string.nav_profile,
        ColaIcons.Person,
        ColaIcons.PersonFilled,
        true,
    );

    companion object {
        /** The sections the shell shows, in order. */
        val shown: List<TopLevel> = entries.filter { it.available }

        /** Where the app opens and where Back leaves from ("exit through home"). */
        val start: TopLevel = Bikes
    }
}
