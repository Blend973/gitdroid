package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.api.*
import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException

class GitHubRepository(context: Context) {
    private val service = RetrofitClient.service
    private val rawService = RetrofitClient.rawFileService
    private val database = GitHubDatabase.getDatabase(context)
    private val dao = database.gitHubDao()
    private val prefs = GitHubPrefHelper(context)

    companion object {
        private const val TAG = "GitHubRepository"
    }

    // Helper to get authorization header
    private fun getAuthHeader(): String? {
        val token = prefs.getAccessToken()
        return if (token != null) "token $token" else null
    }

    fun getAccessToken(): String? = prefs.getAccessToken()
    fun setAccessToken(token: String?) = prefs.setAccessToken(token)

    fun getSavedUser(): String? = prefs.getSavedUser()
    fun setSavedUser(username: String) = prefs.setSavedUser(username)

    // Api wraps
    suspend fun getUser(username: String): Result<GitHubUser> = runCatching {
        service.getUser(username, getAuthHeader())
    }

    suspend fun getUserRepos(username: String): Result<List<GitHubApiRepository>> = runCatching {
        service.getUserRepos(username, sort = "updated", auth = getAuthHeader())
    }

    suspend fun searchRepositories(query: String): Result<List<GitHubApiRepository>> = runCatching {
        val response = service.searchRepositories(query, sort = "stars", auth = getAuthHeader())
        // Log query
        recordSearch(query, "REPO")
        response.items
    }

    suspend fun searchUsers(query: String): Result<List<GitHubUser>> = runCatching {
        val response = service.searchUsers(query, auth = getAuthHeader())
        recordSearch(query, "USER")
        response.items
    }

    suspend fun getRepository(owner: String, name: String): Result<GitHubApiRepository> = runCatching {
        service.getRepository(owner, name, getAuthHeader())
    }

    suspend fun getIssues(owner: String, repo: String, state: String = "open"): Result<List<GitHubIssue>> = runCatching {
        service.getIssues(owner, repo, state, getAuthHeader())
    }

    suspend fun getCommits(owner: String, repo: String): Result<List<GitHubCommit>> = runCatching {
        service.getCommits(owner, repo, getAuthHeader())
    }

    suspend fun getContents(owner: String, repo: String, path: String): Result<List<GitHubContent>> = runCatching {
        if (path.isEmpty()) {
            service.getRootContents(owner, repo, getAuthHeader())
        } else {
            service.getContents(owner, repo, path, getAuthHeader())
        }
    }

    suspend fun getReleases(owner: String, repo: String): Result<List<GitHubRelease>> = runCatching {
        service.getReleases(owner, repo, getAuthHeader())
    }

    suspend fun getCommitDetail(owner: String, repo: String, ref: String): Result<GitHubCommitDetail> = runCatching {
        service.getCommitDetail(owner, repo, ref, getAuthHeader())
    }

    suspend fun getRepoLanguages(owner: String, repo: String): Result<Map<String, Long>> = runCatching {
        val rawMap = service.getRepoLanguages(owner, repo, getAuthHeader())
        rawMap.mapValues { (_, value) ->
            when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull() ?: 0L
                else -> 0L
            }
        }
    }

    suspend fun getRawContent(url: String): Result<String> = runCatching {
        // Use rawService (raw.githubusercontent.com) instead of main API service
        val responseBody = rawService.getRawContent(url, getAuthHeader())
        responseBody.string()
    }

    // Local DB - Bookmarks
    val bookmarkedRepos: Flow<List<BookmarkedRepoEntity>> = dao.getAllBookmarkedRepos()

    suspend fun isBookmarked(id: Long): Boolean {
        return dao.isBookmarked(id)
    }

    suspend fun addBookmark(repo: GitHubApiRepository) {
        val entity = BookmarkedRepoEntity(
            id = repo.id,
            name = repo.name,
            fullName = repo.fullName,
            ownerLogin = repo.owner.login,
            ownerAvatarUrl = repo.owner.avatarUrl,
            description = repo.description,
            stargazersCount = repo.stargazersCount ?: 0,
            language = repo.language
        )
        dao.insertBookmark(entity)
    }

    suspend fun removeBookmarkById(id: Long) {
        dao.deleteBookmarkById(id)
    }

    // Local DB - Searches
    val recentSearches: Flow<List<RecentSearchEntity>> = dao.getRecentSearches()

    private suspend fun recordSearch(query: String, type: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            val latest = dao.getLatestSearch()
            if (latest != null && latest.query.equals(trimmed, ignoreCase = true) && latest.type == type) {
                return
            }
            val entity = RecentSearchEntity(
                query = trimmed,
                type = type,
                timestamp = System.currentTimeMillis()
            )
            dao.insertSearch(entity)
            dao.trimOldSearches()
        }
    }

    suspend fun deleteSearch(id: Long) {
        dao.deleteSearchById(id)
    }

    suspend fun clearHistory() {
        dao.clearAllSearches()
    }

    fun getImageQuality(): String = prefs.getImageQuality()
    fun setImageQuality(quality: String) = prefs.setImageQuality(quality)
    fun getMarkdownImageQuality(): String = prefs.getMarkdownImageQuality()
    fun setMarkdownImageQuality(quality: String) = prefs.setMarkdownImageQuality(quality)

    // Proxy list
    fun getProxyList(): List<String> = prefs.getProxyList()
    fun setProxyList(proxies: List<String>) = prefs.setProxyList(proxies)

    // User-Agent rotation toggle
    fun isUARotationEnabled(): Boolean = prefs.isUARotationEnabled()
    fun setUARotationEnabled(enabled: Boolean) = prefs.setUARotationEnabled(enabled)

    // Scrape release notes from a GitHub release web page (not API).
    // Bypasses api.github.com entirely — only called on-demand per user click.
    suspend fun scrapeReleaseNotes(htmlUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(htmlUrl)
                .userAgent("Mozilla/5.0 (Android 14; Mobile; rv:121.0) Gecko/121.0 Firefox/121.0")
                .timeout(10_000)
                .followRedirects(true)
                .get()
            // GitHub renderer wraps release body in various selectors depending on layout version
            val element = doc.selectFirst("div[data-testid=\"release-body\"] div.markdown-body")
                ?: doc.selectFirst("div.Box-body div.markdown-body")
                ?: doc.selectFirst("article.markdown-body")
                ?: doc.selectFirst("div.markdown-body")
            // Prefer wholeText (preserves newlines over inner blocks) over text()
            element?.wholeText()?.trim()?.let { if (it.isBlank()) null else it }
                ?: element?.text()?.trim()?.let { if (it.isBlank()) null else it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrape release notes from $htmlUrl", e)
            null
        }
    }
}
