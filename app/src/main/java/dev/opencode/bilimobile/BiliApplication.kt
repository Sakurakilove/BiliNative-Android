package dev.opencode.bilimobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class BiliApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val imageClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Referer", "https://www.bilibili.com/")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Mobile Safari/537.36")
                        .removeHeader("Cookie")
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(160L * 1024 * 1024).build() }
            .crossfade(180)
            .build()
    }
}
