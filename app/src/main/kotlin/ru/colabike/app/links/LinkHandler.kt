package ru.colabike.app.links

/**
 * Takes an address that reached the app from outside (the Intent's data is untrusted) and sends it
 * where it belongs: the sign-in code to the sign-in, an address with a screen to the pending
 * navigation, an address without one to the site, everything else nowhere.
 */
class LinkHandler(
    private val parser: AppLinkParser,
    private val site: SiteLinks,
    private val pending: PendingNavigation,
    private val onAuthLink: (String) -> Unit,
    private val onSite: (String) -> Unit,
) {
    fun handle(raw: String?) {
        val link = parser.parse(raw)
        if (link == AppLink.NativeAuth) {
            raw?.let(onAuthLink)
            return
        }
        when (val target = link.target(site)) {
            is LinkTarget.InApp -> pending.offer(target.destination)
            is LinkTarget.OnSite -> onSite(target.url)
            LinkTarget.None -> Unit
        }
    }
}
