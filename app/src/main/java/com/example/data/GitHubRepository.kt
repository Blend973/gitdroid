package com.example.data

import android.content.Context
import com.example.data.api.*
import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class GitHubRepository(context: Context) {
    private val service = RetrofitClient.service
    private val rawService = RetrofitClient.rawFileService
    private val appContext = context.applicationContext
    private val database = GitHubDatabase.getDatabase(context)
    private val dao = database.gitHubDao()
    private val prefs = GitHubPrefHelper(context)
    private val diskCache = JsonDiskCache(context)

    companion object {
        // Keep every new mirror aligned with the OkHttp ETag cache lifetime
        // forced in RetrofitClient: 3 days.
        private const val CACHE_TTL_MS = 3L * 24 * 60 * 60 * 1000
        // Above this, per-navigation tree scans get expensive; use the
        // per-directory contents API instead.
        private const val MAX_TREE_ENTRIES = 15_000
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

    /**
     * Release pages are expensive and rarely change. Serve from the persistent
     * mirror unless the caller explicitly asks for a refresh.
     */
    suspend fun getReleases(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<List<GitHubRelease>> = runCatching {
        if (!forceRefresh) {
            diskCache.getList("releases:$owner/$repo", GitHubRelease::class.java, CACHE_TTL_MS)
                ?.let { return@runCatching it }
        }
        val fresh = service.getReleases(owner, repo, getAuthHeader())
        diskCache.putList("releases:$owner/$repo", GitHubRelease::class.java, fresh)
        fresh
    }

    /**
     * Directory listings are derived on demand from the tree that lives ONLY
     * in the disk cache. The whole repository is never kept as an in-memory
     * map: one git/trees call fills the disk mirror, then each navigation
     * scans the cached entries for just the requested directory.
     *
     * A null [DirectoryListing.entries] means the path is not in the cached
     * tree (missing, truncated, or the tree is too large), so the caller
     * should use the per-directory contents API fallback.
     */
    data class DirectoryListing(val entries: List<GitHubContent>?)

    private val treeMutex = Mutex()

    suspend fun getDirectoryListing(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        forceRefresh: Boolean = false
    ): Result<DirectoryListing> = runCatching {
        val key = "tree:$owner/$repo/$branch"
        val skipKey = "tree-skip:$owner/$repo/$branch"

        // Oversized trees are remembered on disk so every folder tap does not
        // re-download the whole repository just to give up again.
        if (!forceRefresh && diskCache.getString(skipKey, CACHE_TTL_MS) != null) {
            return@runCatching DirectoryListing(entries = null)
        }

        val entries = treeMutex.withLock {
            val cached = if (!forceRefresh) {
                diskCache.getList(key, GitHubTreeEntry::class.java, CACHE_TTL_MS)
            } else null
            cached ?: run {
                val response = service.getGitTree(owner, repo, branch, 1, getAuthHeader())
                if (response.tree.size <= MAX_TREE_ENTRIES) {
                    diskCache.putList(key, GitHubTreeEntry::class.java, response.tree)
                } else {
                    diskCache.putString(skipKey, "1")
                }
                response.tree
            }
        }

        if (entries.size > MAX_TREE_ENTRIES) {
            return@runCatching DirectoryListing(entries = null)
        }
        val listing = withContext(Dispatchers.Default) {
            buildDirectoryListing(entries, path, owner, repo, branch)
        }
        DirectoryListing(entries = listing)
    }

    private fun buildDirectoryListing(
        entries: List<GitHubTreeEntry>,
        parentPath: String,
        owner: String,
        repo: String,
        branch: String
    ): List<GitHubContent>? {
        val target = parentPath.trim('/')
        val listing = mutableListOf<GitHubContent>()
        for (entry in entries) {
            val path = entry.path.trim('/')
            if (path.isBlank()) continue
            if (path.substringBeforeLast('/', "") != target) continue
            val name = path.substringAfterLast('/')
            if (name.isBlank()) continue
            val isDir = entry.type == "tree"
            listing.add(
                GitHubContent(
                    name = name,
                    path = path,
                    sha = entry.sha,
                    size = entry.size ?: 0L,
                    type = if (isDir) "dir" else "file",
                    downloadUrl = if (isDir) null else buildRawUrl(owner, repo, branch, path)
                )
            )
        }
        if (listing.isEmpty()) return null
        listing.sortWith { a, b ->
            val typeOrder = (a.type != "dir").compareTo(b.type != "dir")
            if (typeOrder != 0) typeOrder else a.name.compareTo(b.name, ignoreCase = true)
        }
        return listing
    }

    private fun buildRawUrl(owner: String, repo: String, branch: String, path: String): String {
        fun encode(segment: String): String = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        val branchPath = branch.split('/').joinToString("/") { encode(it) }
        val filePath = path.split('/').joinToString("/") { encode(it) }
        return "https://raw.githubusercontent.com/$owner/$repo/$branchPath/$filePath"
    }

    /**
     * Commit diffs are fetched from the public .patch endpoint on github.com
     * instead of api.github.com/commits/{sha}. Parsed and cached locally, so
     * reopening a commit detail costs zero network calls.
     */
    suspend fun getCommitPatchFiles(
        owner: String,
        repo: String,
        sha: String
    ): Result<List<GitHubCommitFile>> = runCatching {
        diskCache.getList("commit-files:$owner/$repo/$sha", GitHubCommitFile::class.java, CACHE_TTL_MS)
            ?.let { return@runCatching it }

        val url = "https://github.com/$owner/$repo/commit/$sha.patch"
        // Public repos serve the raw patch directly from github.com. Private
        // repos can reject that unauthenticated web endpoint, so fall back to
        // the REST commit-detail call only when the patch route fails.
        val patchFiles = runCatching {
            val patch = withContext(Dispatchers.IO) {
                rawService.getRawContent(url, getAuthHeader()).string()
            }
            withContext(Dispatchers.Default) { parseUnifiedPatch(patch) }
        }.getOrNull()

        val files = patchFiles ?: run {
            val detail = service.getCommitDetail(owner, repo, sha, getAuthHeader())
            detail.files.orEmpty()
        }
        diskCache.putList("commit-files:$owner/$repo/$sha", GitHubCommitFile::class.java, files)
        files
    }

    private fun parseUnifiedPatch(patch: String): List<GitHubCommitFile> {
        val files = mutableListOf<GitHubCommitFile>()
        var oldPath = ""
        var newPath = ""
        var status = "modified"
        var patchBody: MutableList<String>? = null
        var additions = 0
        var deletions = 0
        var inHunk = false

        fun flush() {
            if (newPath.isBlank() && oldPath.isBlank()) return
            val body = patchBody?.joinToString("\n")?.takeIf { it.isNotBlank() }
            files.add(
                GitHubCommitFile(
                    filename = newPath.ifBlank { oldPath },
                    status = status,
                    additions = additions,
                    deletions = deletions,
                    changes = additions + deletions,
                    patch = body
                )
            )
            oldPath = ""
            newPath = ""
            status = "modified"
            patchBody = null
            additions = 0
            deletions = 0
            inHunk = false
        }

        for (line in patch.lines()) {
            if (line.startsWith("diff --git ")) {
                flush()
                val rest = line.removePrefix("diff --git ")
                val split = rest.lastIndexOf(" b/")
                if (split >= 0) {
                    oldPath = rest.substring(2, split)
                    newPath = rest.substring(split + 3)
                }
                patchBody = mutableListOf()
                continue
            }
            // flush() mutates patchBody, so pin the non-null list in a local
            // val the compiler can smart cast for the rest of this iteration.
            val body = patchBody ?: continue

            when {
                line.startsWith("new file mode") -> status = "added"
                line.startsWith("deleted file mode") -> status = "removed"
                line.startsWith("rename to ") -> newPath = line.removePrefix("rename to ").trim()
                line.startsWith("--- /dev/null") -> status = "added"
                line.startsWith("+++ /dev/null") -> status = "removed"
                line.startsWith("+++ b/") -> newPath = line.removePrefix("+++ b/").trim()
                line.startsWith("@@") -> {
                    inHunk = true
                    body.add(line)
                }
                inHunk -> {
                    when {
                        line.startsWith("+") -> {
                            additions++
                            body.add(line)
                        }
                        line.startsWith("-") -> {
                            deletions++
                            body.add(line)
                        }
                        line.startsWith(" ") || line.startsWith("\\") -> body.add(line)
                        line.startsWith("Binary files") -> body.add(line)
                    }
                }
            }
        }
        flush()
        return files
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
        withContext(Dispatchers.IO) { responseBody.string() }
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

    // Rotating IP identity pool
    fun getIpPool(): List<String> = prefs.getIpPool()
    fun setIpPool(ips: List<String>) = prefs.setIpPool(ips)

    // User-Agent rotation toggle
    fun isUARotationEnabled(): Boolean = prefs.isUARotationEnabled()
    fun setUARotationEnabled(enabled: Boolean) = prefs.setUARotationEnabled(enabled)

    /**
     * Wipes every cache layer without touching user data:
     *  • OkHttp API + raw response caches
     *  • JSON mirror (releases, file trees, commit patches, scraped notes)
     *  • Coil memory + disk image caches
     * Bookmarks, recent searches, tokens and settings stay untouched.
     */
    suspend fun clearAllCaches() = withContext(Dispatchers.IO) {
        diskCache.clearAll()
        RetrofitClient.clearHttpCaches()
        try {
            val imageLoader = coil.Coil.imageLoader(appContext)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (_: Exception) {
        }
    }

    // Scrape release notes from a GitHub release web page (not API).
    // Persistent mirror means a release opened twice scrapes only once.
    suspend fun scrapeReleaseNotes(htmlUrl: String): String? = withContext(Dispatchers.IO) {
        diskCache.getString("release-notes:$htmlUrl", CACHE_TTL_MS)?.let { return@withContext it }
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
            val text = element?.wholeText()?.trim()?.takeIf { it.isNotBlank() }
                ?: element?.text()?.trim()?.takeIf { it.isNotBlank() }
            if (text != null) diskCache.putString("release-notes:$htmlUrl", text)
            text
        } catch (_: Exception) {
            null
        }
    }
}
