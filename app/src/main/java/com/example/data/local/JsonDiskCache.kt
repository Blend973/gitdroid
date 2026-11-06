package com.example.data.local

import android.content.Context
import com.example.data.api.RetrofitClient
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Small persistent mirror for expensive payloads.
 *
 * SharedPreferences is deliberately avoided: release bodies and commit patches
 * can be hundreds of kilobytes, and stuffing them into XML preferences keeps
 * every byte in RAM forever. Files under cacheDir persist across process death
 * while staying reclaimable by the OS, and writes are periodically pruned so
 * the mirror cannot grow without bound.
 */
class JsonDiskCache(context: Context) {

    private val directory = createCacheDirectory(context)
    private val writeCount = AtomicInteger(0)

    companion object {
        // Same lifetime as the OkHttp ETag cache (3 days).
        private const val MAX_ENTRY_AGE_MS = 3L * 24 * 60 * 60 * 1000
        private const val MAX_CACHE_BYTES = 50L * 1024 * 1024
        private const val PRUNE_EVERY_WRITES = 16
    }

    private fun maybePrune() {
        if (writeCount.incrementAndGet() % PRUNE_EVERY_WRITES != 0) return
        val files = directory.listFiles() ?: return
        val now = System.currentTimeMillis()
        var total = 0L
        val alive = ArrayList<File>(files.size)
        for (file in files) {
            if (now - file.lastModified() > MAX_ENTRY_AGE_MS) {
                file.delete()
            } else {
                total += file.length()
                alive.add(file)
            }
        }
        if (total <= MAX_CACHE_BYTES) return
        alive.sortBy { it.lastModified() }
        for (file in alive) {
            if (total <= MAX_CACHE_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.cache")
    }

    suspend fun getString(key: String, ttlMs: Long): String? = withContext(Dispatchers.IO) {
        try {
            val file = fileFor(key)
            if (!file.exists()) return@withContext null
            if (System.currentTimeMillis() - file.lastModified() > ttlMs) {
                file.delete()
                return@withContext null
            }
            file.readText()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        try {
            val file = fileFor(key)
            file.writeText(value)
            file.setLastModified(System.currentTimeMillis())
        } catch (_: Exception) {
        }
        maybePrune()
    }

    suspend fun <T> get(key: String, type: Type, ttlMs: Long): T? = withContext(Dispatchers.IO) {
        val raw = getString(key, ttlMs) ?: return@withContext null
        try {
            val adapter = RetrofitClient.moshi.adapter<T>(type)
            adapter.fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun <T> put(key: String, type: Type, value: T) = withContext(Dispatchers.IO) {
        try {
            val adapter = RetrofitClient.moshi.adapter<T>(type)
            putString(key, adapter.toJson(value) ?: return@withContext)
        } catch (_: Exception) {
        }
    }

    suspend fun <T> getList(key: String, element: Class<T>, ttlMs: Long): List<T>? {
        val type = Types.newParameterizedType(List::class.java, element)
        return get(key, type, ttlMs)
    }

    suspend fun <T> putList(key: String, element: Class<T>, value: List<T>) {
        val type = Types.newParameterizedType(List::class.java, element)
        put(key, type, value)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            directory.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {
        }
    }
}

/**
 * Cache files used to live in "api_cache". Rename that directory once so an
 * existing 3-day mirror survives the update instead of being re-downloaded.
 */
private fun createCacheDirectory(context: Context): File {
    val root = context.cacheDir
    val target = File(root, "local_cache")
    val legacy = File(root, "api_cache")
    if (!target.exists() && legacy.exists()) {
        legacy.renameTo(target)
    }
    // If the rename could not happen, drop the old copy rather than leave
    // orphaned cache files behind. Everything in there is replaceable.
    if (legacy.exists()) legacy.deleteRecursively()
    target.mkdirs()
    return target
}
