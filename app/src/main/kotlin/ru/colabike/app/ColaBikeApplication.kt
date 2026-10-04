package ru.colabike.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import ru.colabike.app.push.PushChannel

class ColaBikeApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // The channels are permanent and exist before the first push, so that the person can set
        // each of them in the system's settings. Creating them again changes none of their choices.
        PushChannel.ensure(this)
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
