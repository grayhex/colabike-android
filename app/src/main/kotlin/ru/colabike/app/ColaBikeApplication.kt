package ru.colabike.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import ru.colabike.app.push.PushChannel
import ru.colabike.app.push.RuStorePushProvider
import ru.colabike.app.push.StoredPushBinding

class ColaBikeApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // The channels are permanent and exist before the first push, so that the person can set
        // each of them in the system's settings. Creating them again changes none of their choices.
        PushChannel.ensure(this)
        // The push SDK works in one process only. It starts here only for a phone that is already
        // registered (a message may arrive at any time), and only when the build has the owner's
        // project at RuStore: a development build talks to nobody, and so does a phone whose
        // person never turned push on (the registrar starts the SDK after their yes).
        if (getProcessName() == packageName && BuildConfig.RUSTORE_PROJECT_ID.isNotBlank()) {
            val registered =
                StoredPushBinding(getSharedPreferences("push-binding", MODE_PRIVATE)).current()
            if (registered != null) RuStorePushProvider.start(this, BuildConfig.RUSTORE_PROJECT_ID)
        }
    }

    val graph: AppGraph by lazy {
        AppGraph(this) {
            // Pictures of the previous account (private media, cola#324) leave with it.
            SingletonImageLoader.get(this).run {
                memoryCache?.clear()
                diskCache?.clear()
            }
        }
    }

    // Images go through the app's OkHttp client, so private media (cola#324) gets the Bearer
    // token for the site's host only; other hosts never see it.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.httpClient })) }
            .build()
}
