package ru.colabike.app.push

/**
 * The conversation the person is looking at right now, if any: set while its screen is in front,
 * cleared when it leaves. A message of that conversation is already on screen, so it makes no
 * notification (see [PushPolicy]); in any other conversation, or with the app in the background, it
 * does. Held in memory only: the cid is not written anywhere.
 */
class VisibleConversation {
    @Volatile private var cid: String? = null

    fun show(cid: String) {
        this.cid = cid
    }

    /** Clears only if [cid] is still the one shown: the next screen may have set its own first. */
    fun hide(cid: String) {
        if (this.cid == cid) this.cid = null
    }

    /** True while the message's conversation is the one in front. */
    fun isShowing(envelope: PushEnvelope): Boolean =
        envelope.target.type == "chat" && envelope.target.ref != null && envelope.target.ref == cid
}
