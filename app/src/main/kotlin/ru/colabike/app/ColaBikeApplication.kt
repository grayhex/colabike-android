package ru.colabike.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

class ColaBikeApplication : Application(), SingletonImageLoader.Factory {
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
