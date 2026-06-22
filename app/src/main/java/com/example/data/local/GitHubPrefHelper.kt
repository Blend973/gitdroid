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
}
