package ru.colabike.app.push

import android.app.Application
import android.content.pm.PackageManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.rustore.sdk.core.exception.RuStoreNotInstalledException
import ru.rustore.sdk.core.feature.model.FeatureAvailabilityResult
import ru.rustore.sdk.core.tasks.OnFailureListener
import ru.rustore.sdk.core.tasks.OnSuccessListener
import ru.rustore.sdk.core.tasks.Task
import ru.rustore.sdk.pushclient.RuStorePushClient
import ru.rustore.sdk.pushclient.messaging.exception.RuStorePushClientException

/**
 * The push provider of RuStore (docs/adr/0017) behind [PushProvider]: the rest of the app knows
 * `PushAvailability` and a token, never the SDK's types. It needs the project the owner registered
 * the app under; without one ([projectId] empty) the build offers no push and the SDK is never
 * started, so a build made for development talks to nobody. The SDK also starts only for a person
 * who turned push on (see [PushProvider.start]).
 *
 * The SDK's own log is silenced: its default logger prints what it does, and an address of a phone
 * is not for the system log.
 */
class RuStorePushProvider(
    private val application: Application,
    override val projectId: String,
) : PushProvider {
    override fun start() = start(application, projectId)

    override suspend fun availability(): PushAvailability {
        if (projectId.isBlank()) return PushAvailability.NotConfigured
        // Not started (nobody has said yes yet): the library is not asked, the phone is. RuStore's
        // own application is what delivers; whether it is signed in is for the library to say.
        if (!RuStorePushClient.isInitialized) {
            return if (storeInstalled()) PushAvailability.Available
            else PushAvailability.NoDistributor
        }
        return try {
            when (val result = RuStorePushClient.checkPushAvailability().suspended()) {
                is FeatureAvailabilityResult.Available -> PushAvailability.Available
                is FeatureAvailabilityResult.Unavailable ->
                    when (result.cause) {
                        is RuStoreNotInstalledException,
                        is RuStorePushClientException.HostAppNotInstalledException ->
                            PushAvailability.NoDistributor
                        else -> PushAvailability.Failed
                    }
                else -> PushAvailability.Failed
            }
        } catch (_: Exception) {
            PushAvailability.Failed
        }
    }

    override suspend fun token(): String? =
        if (projectId.isBlank() || !RuStorePushClient.isInitialized) null
        else
            try {
                RuStorePushClient.getToken().suspended().takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }

    override suspend fun deleteToken() {
        if (projectId.isBlank() || !RuStorePushClient.isInitialized) return
        RuStorePushClient.deleteToken().suspended()
    }

    private fun storeInstalled(): Boolean =
        try {
            application.packageManager.getPackageInfo(STORE_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    companion object {
        /** RuStore's application (the SDK's manifest lets the app see it). */
        private const val STORE_PACKAGE = "ru.vk.store"

        /**
         * Starts the SDK in the main process (the SDK does not work in several at once). Does
         * nothing without a project, and nothing a second time.
         */
        @Synchronized
        fun start(application: Application, projectId: String) {
            if (projectId.isBlank() || RuStorePushClient.isInitialized) return
            RuStorePushClient.init(application, projectId, SilentLogger)
        }
    }
}

/** Waits for a task of the SDK without blocking a thread. */
private suspend fun <T> Task<T>.suspended(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener(
        object : OnSuccessListener<T> {
            override fun onSuccess(result: T) {
                if (continuation.isActive) continuation.resume(result)
            }
        }
    )
    addOnFailureListener(
        object : OnFailureListener {
            override fun onFailure(throwable: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(throwable)
            }
        }
    )
    continuation.invokeOnCancellation { cancel() }
}
