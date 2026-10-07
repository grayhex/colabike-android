package ru.colabike.app.rides.map

/**
 * The style of the OpenStreetMap basemap under a route. [url] is a MapLibre style the library
 * fetches itself; [builtIn] is the style the app ships with (OpenFreeMap), as against one the owner
 * put in `colabike.mapStyleUrl`. A built-in style is shown with the app's own caption of its
 * sources; an override brings its own attribution through the library's button.
 */
internal data class BasemapStyle(val url: String, val builtIn: Boolean) {
    companion object {
        /**
         * OpenFreeMap: OpenStreetMap data as vector tiles, no key and no registration (ADR 0024).
         * The light style is the quiet one, the dark style keeps the page's dark at night.
         */
        const val LIGHT_URL = "https://tiles.openfreemap.org/styles/positron"
        const val DARK_URL = "https://tiles.openfreemap.org/styles/dark"

        /** Where the licence of the data is read and what the caption's tap opens. */
        const val COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

        /**
         * The style under the route: the owner's override when it is a usable `https` address, the
         * built-in style otherwise, never nothing. A wrong override is not an error to show to a
         * rider: the map is simply the usual one.
         */
        fun choose(override: String?, dark: Boolean): BasemapStyle {
            val given = override?.trim()?.takeIf { it.startsWith("https://") && it.length > 8 }
            return if (given != null) BasemapStyle(given, builtIn = false)
            else BasemapStyle(if (dark) DARK_URL else LIGHT_URL, builtIn = true)
        }
    }
}
