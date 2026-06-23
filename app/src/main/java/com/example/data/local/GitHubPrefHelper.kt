package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class GitHubPrefHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "github_fast_prefs",
        Context.MODE_PRIVATE
    )

    fun getAccessToken(): String? {
        val s = prefs.getString("access_token", null)
        return if (s.isNullOrBlank()) null else s
    }

    fun setAccessToken(token: String?) {
        prefs.edit().putString("access_token", token?.trim()).apply()
    }

    fun getSavedUser(): String? {
        return prefs.getString("saved_user", null)
    }

    fun setSavedUser(username: String) {
        prefs.edit().putString("saved_user", username.trim()).apply()
    }

    fun getImageQuality(): String {
        val q = prefs.getString("image_quality", "ON") ?: "ON"
        return if (q == "DISABLE" || q == "OFF") "OFF" else "ON"
    }

    fun setImageQuality(quality: String) {
        val mapped = if (quality.uppercase() == "DISABLE" || quality.uppercase() == "OFF") "OFF" else "ON"
        prefs.edit().putString("image_quality", mapped).apply()
    }

    fun getMarkdownImageQuality(): String {
        val q = prefs.getString("markdown_image_quality", "ON") ?: "ON"
        return if (q == "DISABLE" || q == "OFF") "OFF" else "ON"
    }

    fun setMarkdownImageQuality(quality: String) {
        val mapped = if (quality.uppercase() == "DISABLE" || quality.uppercase() == "OFF") "OFF" else "ON"
        prefs.edit().putString("markdown_image_quality", mapped).apply()
    }

    // Proxy list: stored as newline-separated "host:port" entries
    fun getProxyList(): List<String> {
        val raw = prefs.getString("proxy_list", null) ?: return emptyList()
        return raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun setProxyList(proxies: List<String>) {
        val raw = proxies.joinToString("\n")
        prefs.edit().putString("proxy_list", raw).apply()
    }

    // User-Agent rotation toggle
    fun isUARotationEnabled(): Boolean {
        return prefs.getBoolean("ua_rotation", true)
    }

    fun setUARotationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ua_rotation", enabled).apply()
    }
}
