package com.example.data.api

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object RetrofitClient {
    private var cacheDir: File? = null

    fun initialize(context: Context) {
        if (cacheDir == null) {
            cacheDir = context.cacheDir
        }
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.example.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    // ─── Round-robin proxy selector ──────────────────────────────────────────
    // Shared, mutable proxy selector. Call updateProxies() at runtime to swap
    // the pool without rebuilding the OkHttp client. ProxySelector is called
    // per-connection, so setting a small connection pool ensures faster rotation.
    val proxySelector = RoundRobinProxySelector()

    class RoundRobinProxySelector : ProxySelector() {
        @Volatile
        private var proxies: List<Pair<String, Int>> = emptyList()
        private val index = AtomicInteger(0)

        fun updateProxies(entries: List<Pair<String, Int>>) {
            proxies = entries
            index.set(0)
        }

        fun proxyCount(): Int = proxies.size

        override fun select(uri: URI?): MutableList<Proxy> {
            val current = proxies
            if (current.isEmpty()) return mutableListOf(Proxy.NO_PROXY)
            val idx = index.getAndIncrement() % current.size
            val (host, port) = current[idx]
            return mutableListOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            // Failing proxy — could mark as dead and skip in future iterations
        }
    }

    // ─── User-agent rotation pool ────────────────────────────────────────────
    // Realistic browser UAs that rotate on every request to evade WAF
    // fingerprinting based on the User-Agent header.
    @Volatile
    var uaRotationEnabled = true

    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0",
    )

    private val uaIndex = AtomicInteger(0)

    // ─── OkHttp clients ──────────────────────────────────────────────────────

    private val okHttpClient by lazy {
        val cacheSize = 20L * 1024 * 1024 // 20 MB cache

        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .proxySelector(proxySelector)
            // Small connection pool forces quicker proxy rotation
            .connectionPool(okhttp3.ConnectionPool(2, 30, TimeUnit.SECONDS))
            .addInterceptor(loggingInterceptor)
            // User-Agent rotation interceptor
            .addInterceptor { chain ->
                val ua = if (uaRotationEnabled) {
                    userAgents[uaIndex.getAndIncrement() % userAgents.size]
                } else {
                    "GitDroid/1.0"
                }
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", ua)
                    .build()
                chain.proceed(request)
            }
            // Network interceptor: force all successful GET responses to be cacheable for 3 days.
            // OkHttp's built-in cache stores ETags from GitHub, then sends conditional
            // If-None-Match requests on stale cache. A 304 response means served-from-cache
            // and does NOT count against GitHub's rate limit.
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().method == "GET" && response.isSuccessful) {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=259200") // 3 days
                        .removeHeader("Pragma")
                        .removeHeader("Expires")
                        .build()
                } else {
                    response
                }
            }

        cacheDir?.let { dir ->
            builder.cache(Cache(File(dir, "http_cache"), cacheSize))
        }

        builder.build()
    }

    // Separate service for raw.githubusercontent.com content.
    // Raw content fetches bypass api.github.com entirely, so they don't consume
    // the same rate limit quota. Uses its own cache pool.
    private val rawOkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS) // files can be larger
            .followRedirects(true)
            .proxySelector(proxySelector)
            .connectionPool(okhttp3.ConnectionPool(2, 30, TimeUnit.SECONDS))
            // User-Agent rotation for raw requests too
            .addInterceptor { chain ->
                val ua = if (uaRotationEnabled) {
                    userAgents[uaIndex.getAndIncrement() % userAgents.size]
                } else {
                    "GitDroid/1.0"
                }
                val request = chain.request().newBuilder()
                    .header("User-Agent", ua)
                    .build()
                chain.proceed(request)
            }
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().method == "GET" && response.isSuccessful) {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=259200") // 3 days for files
                        .removeHeader("Pragma")
                        .removeHeader("Expires")
                        .build()
                } else {
                    response
                }
            }

        cacheDir?.let { dir ->
            builder.cache(Cache(File(dir, "raw_cache"), 20L * 1024 * 1024))
        }

        builder.build()
    }

    // Separate raw content service (raw.githubusercontent.com).
    // Raw content fetches bypass api.github.com entirely, so they don't consume
    // the same rate limit quota. Uses its own cache pool.
    val rawFileService: GitHubService by lazy {
        Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .client(rawOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubService::class.java)
    }

    val service: GitHubService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubService::class.java)
    }
}
