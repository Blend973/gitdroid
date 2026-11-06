package com.example.data.api

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object RetrofitClient {
    private var cacheDir: File? = null

    // Keep the OkHttp ETag caches aligned with the JSON mirror's 50 MB cap.
    private const val HTTP_CACHE_BYTES = 50L * 1024 * 1024

    fun initialize(context: Context) {
        if (cacheDir == null) {
            cacheDir = context.cacheDir
        }
    }

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // ─── Rotating IP identity pool ────────────────────────────────────────────
    // GitHub (and most public endpoints) bucket anonymous traffic by source IP.
    // Instead of routing through SOCKS/HTTP proxies we keep a pool of plain IP
    // entries and stamp every outgoing request with the next one in the ring.
    // The pool is shared by the API client and the raw/web client, so requests
    // always advance through the same circular list.
    @Volatile
    private var ipPool: List<String> = emptyList()

    private val ipIndex = AtomicInteger(0)

    fun updateIpPool(entries: List<String>) {
        ipPool = entries.mapNotNull { normalizeIpEntry(it) }
        ipIndex.set(0)
    }

    fun ipPoolSize(): Int = ipPool.size

    /** Accepts bare IPs, optional `ip:port`, bracketed IPv6 and protocol prefixes. */
    fun normalizeIpEntry(raw: String): String? {
        var value = raw.substringBefore('#').trim()
        if (value.isBlank()) return null
        value = value
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("socks5://")
            .removePrefix("socks4://")

        // [2001:db8::1]:8080 -> 2001:db8::1
        if (value.startsWith("[") && value.contains("]")) {
            return value.substringAfter("[").substringBefore("]").trim().ifBlank { null }
        }

        // 192.168.1.5:8080 -> 192.168.1.5 (ports are meaningless for identity headers)
        if (value.count { it == ':' } == 1) {
            val host = value.substringBefore(':').trim()
            val port = value.substringAfter(':').trim()
            if (host.isNotBlank() && port.toIntOrNull() != null) return host
        }

        return value.ifBlank { null }
    }

    private fun nextIp(): String? {
        val current = ipPool
        if (current.isEmpty()) return null
        val idx = ipIndex.getAndIncrement() % current.size
        return current[idx]
    }

    private fun Request.Builder.applyRotatingIdentity(): Request.Builder {
        val ip = nextIp() ?: return this
        // Rotate every identity header a CDN/WAF may consult. Requests still use
        // the device's real socket, but each call presents a different origin IP.
        header("X-Forwarded-For", ip)
        header("X-Real-IP", ip)
        header("X-Client-IP", ip)
        header("X-Originating-IP", ip)
        header("CF-Connecting-IP", ip)
        header("True-Client-IP", ip)
        header("Forwarded", "for=\"$ip\";proto=https")
        return this
    }

    // ─── User-agent rotation pool ────────────────────────────────────────────
    // Realistic browser UAs rotate on every request to avoid WAF fingerprinting
    // based on the User-Agent header.
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

    private fun rotatedUserAgent(): String = if (uaRotationEnabled) {
        userAgents[uaIndex.getAndIncrement() % userAgents.size]
    } else {
        "GitDroid/1.0"
    }

    // ─── OkHttp clients ──────────────────────────────────────────────────────

    private val okHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // No proxy selector: traffic goes direct, only the presented IP
            // identity changes per request.
            .connectionPool(okhttp3.ConnectionPool(2, 30, TimeUnit.SECONDS))
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", rotatedUserAgent())
                    .applyRotatingIdentity()
                    .build()
                chain.proceed(request)
            }
            // Network interceptor: force successful GET responses to be cacheable.
            // OkHttp sends conditional If-None-Match requests on stale entries;
            // a 304 is served from disk and does not consume GitHub's quota.
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
            builder.cache(Cache(File(dir, "http_cache"), HTTP_CACHE_BYTES))
        }

        builder.build()
    }

    // Separate service for raw.githubusercontent.com and github.com web content.
    // These hosts bypass api.github.com entirely, so they do not consume the
    // same REST rate-limit quota. Own cache, same rotating identity.
    private val rawOkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS) // files can be larger
            .followRedirects(true)
            .connectionPool(okhttp3.ConnectionPool(2, 30, TimeUnit.SECONDS))
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", rotatedUserAgent())
                    .applyRotatingIdentity()
                    .build()
                chain.proceed(request)
            }
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
            builder.cache(Cache(File(dir, "raw_cache"), HTTP_CACHE_BYTES))
        }

        builder.build()
    }

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

    /** Evict both OkHttp disk caches. Safe to call while clients are alive. */
    fun clearHttpCaches() {
        try {
            okHttpClient.cache?.evictAll()
        } catch (_: Exception) {
        }
        try {
            rawOkHttpClient.cache?.evictAll()
        } catch (_: Exception) {
        }
    }
}
