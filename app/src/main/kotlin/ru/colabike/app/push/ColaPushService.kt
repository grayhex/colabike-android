package ru.colabike.app.push

import ru.colabike.app.ColaBikeApplication
import ru.rustore.sdk.pushclient.messaging.exception.RuStorePushClientException
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService

/**
 * Where RuStore hands a message to the app (docs/adr/0017). The service is exported, because the
 * RuStore application binds to it from outside; for that reason it trusts nothing it is given: the
 * one string it reads goes through [PushHandler], which checks the whole envelope against the
 * phone's binding and the time before anything is shown. A message that carries no envelope, or a
 * damaged one, is dropped without a trace.
 */
class ColaPushService : RuStoreMessagingService() {
    private val graph
        get() = (application as ColaBikeApplication).graph

    override fun onNewToken(token: String) {
        graph.pushRegistrar.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val raw = message.data[ENVELOPE] ?: return
        graph.pushHandler.onMessage(raw)
    }

    override fun onDeletedMessages() {
        // Messages were dropped by the provider: what the person has not seen is in the inbox.
    }

    override fun onError(errors: List<RuStorePushClientException>) {
        // Nothing to show or to log: a message of an error may carry an address.
    }

    private companion object {
        /** The key of the envelope in the data of a message (the backend's adapter, cola#342). */
        const val ENVELOPE = "envelope"
    }
}
