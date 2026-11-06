package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubRepository
import com.example.data.api.*
import com.example.data.local.BookmarkedRepoEntity
import com.example.data.local.RecentSearchEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    object Dashboard : Screen()
    data class RepositoryDetail(val owner: String, val name: String) : Screen()
    data class UserProfile(val username: String) : Screen()
    object Settings : Screen()
    data class FileView(val owner: String, val repo: String, val filePath: String, val downloadUrl: String) : Screen()
}

class GitHubViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GitHubRepository(application)

    // Backstack-based custom instant navigation
    private val navHistory = mutableListOf<Screen>()
    val currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val isBackEnabled = MutableStateFlow(false)

    // Global settings / config
    val accessToken = MutableStateFlow(repository.getAccessToken() ?: "")
    val activeHostUser = MutableStateFlow<String?>(repository.getSavedUser())
    val imageQuality = MutableStateFlow(repository.getImageQuality())
    val markdownImageQuality = MutableStateFlow(repository.getMarkdownImageQuality())

    // Dashboard feeds & search
    val recentSearches: StateFlow<List<RecentSearchEntity>> = repository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    val bookmarkedRepos: StateFlow<List<BookmarkedRepoEntity>> = repository.bookmarkedRepos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    val searchQuery = MutableStateFlow("")
    val searchType = MutableStateFlow("REPO") // "REPO" or "USER"
    val isSearching = MutableStateFlow(false)
    val searchReposResult = MutableStateFlow<List<GitHubApiRepository>>(emptyList())
    val searchUsersResult = MutableStateFlow<List<GitHubUser>>(emptyList())
    val searchError = MutableStateFlow<String?>(null)

    // Profiles
    val userProfileLoading = MutableStateFlow(false)
    val userProfileData = MutableStateFlow<GitHubUser?>(null)
    val userProfileRepos = MutableStateFlow<List<GitHubApiRepository>>(emptyList())
    val userProfileError = MutableStateFlow<String?>(null)

    // Loaded Repository details
    val repoDetailLoading = MutableStateFlow(false)
    val repoDetailData = MutableStateFlow<GitHubApiRepository?>(null)
    val repoDetailIsBookmarked = MutableStateFlow(false)
    val repoDetailError = MutableStateFlow<String?>(null)

    // Sub-components of repos
    val repoActiveTab = MutableStateFlow("CODE") // "CODE", "ISSUES", "COMMITS"
    
    // Code Contents
    val codePath = MutableStateFlow("") // e.g. "src/main"
    val codeContentsLoading = MutableStateFlow(false)
    val codeContents = MutableStateFlow<List<GitHubContent>>(emptyList())
    val codeError = MutableStateFlow<String?>(null)
    private val codePathStack = mutableListOf<String>()
    private var contentsJob: Job? = null

    // Issues
    val issuesLoading = MutableStateFlow(false)
    val issuesState = MutableStateFlow("open") // "open" or "closed"
    val issuesList = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val issuesError = MutableStateFlow<String?>(null)

    // Commits
    val commitsLoading = MutableStateFlow(false)
    val commitsList = MutableStateFlow<List<GitHubCommit>>(emptyList())
    val commitsError = MutableStateFlow<String?>(null)

    // Code Contents File View
    val activeViewFileName = MutableStateFlow("")
    val activeViewFileContent = MutableStateFlow("")
    val activeViewFileLoading = MutableStateFlow(false)
    val activeViewFileError = MutableStateFlow<String?>(null)

    // README Content flows for in-page display
    val readmeName = MutableStateFlow<String?>(null)
    val readmeContent = MutableStateFlow<String?>(null)
    val readmeDownloadUrl = MutableStateFlow<String?>(null)
    val readmeLoading = MutableStateFlow(false)

    // Languages stats
    val repoLanguagesLoading = MutableStateFlow(false)
    val repoLanguages = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Commit Details (for Diffs)
    val commitDetailLoading = MutableStateFlow(false)
    val activeCommitDetail = MutableStateFlow<GitHubCommitDetail?>(null)
    val commitDetailError = MutableStateFlow<String?>(null)
    private var commitDetailJob: kotlinx.coroutines.Job? = null

    // Releases list
    val releasesLoading = MutableStateFlow(false)
    val releasesList = MutableStateFlow<List<GitHubRelease>>(emptyList())
    val releasesError = MutableStateFlow<String?>(null)
    private var releasesLoadedFor: String? = null

    // Release notes scraping (on-demand from web page, not API)
    val releaseNotesLoading = MutableStateFlow(false)
    val releaseNotesContent = MutableStateFlow<String?>(null)

    // Rotating IP identity + UA rotation settings
    val ipPool = MutableStateFlow<List<String>>(emptyList())
    val uaRotationEnabled = MutableStateFlow(true)

    // Cache maintenance status
    val cacheClearing = MutableStateFlow(false)
    val cachesCleared = MutableStateFlow(false)

    init {
        // Load persisted IP pool into the shared rotating identity ring
        val storedIps = repository.getIpPool()
        ipPool.value = storedIps
        pushIpsToSelector(storedIps)
        uaRotationEnabled.value = repository.isUARotationEnabled()
        RetrofitClient.uaRotationEnabled = uaRotationEnabled.value

        // Dynamically update back enabled state when search query changes
        viewModelScope.launch {
            searchQuery.collect {
                updateBackEnabled()
            }
        }
    }

    fun saveIpPool(ips: List<String>) {
        ipPool.value = ips
        repository.setIpPool(ips)
        pushIpsToSelector(ips)
    }

    private fun pushIpsToSelector(ips: List<String>) {
        RetrofitClient.updateIpPool(ips)
    }

    fun toggleUARotation(enabled: Boolean) {
        uaRotationEnabled.value = enabled
        repository.setUARotationEnabled(enabled)
        RetrofitClient.uaRotationEnabled = enabled
    }

    private fun updateBackEnabled() {
        val current = currentScreen.value
        val hasDirStack = current is Screen.RepositoryDetail && codePathStack.isNotEmpty()
        val hasDashboardSearch = current is Screen.Dashboard && searchQuery.value.isNotEmpty()
        isBackEnabled.value = navHistory.isNotEmpty() || hasDirStack || hasDashboardSearch
    }

    // Navigation triggers
    fun navigateTo(screen: Screen) {
        if (currentScreen.value != screen) {
            navHistory.add(currentScreen.value)
            currentScreen.value = screen
            updateBackEnabled()
        }
    }

    fun navigateBack(): Boolean {
        val current = currentScreen.value
        if (current is Screen.Dashboard && searchQuery.value.isNotEmpty()) {
            clearSearch()
            return true
        }
        if (current is Screen.RepositoryDetail) {
            if (navigateUpDirectory()) {
                updateBackEnabled()
                return true
            }
        }
        if (navHistory.isNotEmpty()) {
            val prev = navHistory.removeAt(navHistory.size - 1)
            currentScreen.value = prev
            updateBackEnabled()
            return true
        }
        updateBackEnabled()
        return false
    }

    // Config saves
    fun saveToken(token: String) {
        repository.setAccessToken(token)
        accessToken.value = token
    }

    fun saveHostUser(user: String) {
        repository.setSavedUser(user)
        activeHostUser.value = user
    }

    fun setImageQuality(quality: String) {
        repository.setImageQuality(quality)
        imageQuality.value = quality
    }

    fun setMarkdownImageQuality(quality: String) {
        repository.setMarkdownImageQuality(quality)
        markdownImageQuality.value = quality
    }

    // Search execute
    fun performSearch() {
        val query = searchQuery.value.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            isSearching.value = true
            searchError.value = null
            if (searchType.value == "REPO") {
                val res = repository.searchRepositories(query)
                res.fold(
                    onSuccess = { rawList ->
                        // Boost repositories with rich relevancy heuristics (exact matches first, stars tie-breaker)
                        val sortedList = rawList.sortedWith(
                            compareByDescending<GitHubApiRepository> {
                                it.name.equals(query, ignoreCase = true)
                            }.thenByDescending {
                                it.name.startsWith(query, ignoreCase = true)
                            }.thenByDescending {
                                it.name.contains(query, ignoreCase = true)
                            }.thenByDescending {
                                it.description?.contains(query, ignoreCase = true) == true
                            }.thenByDescending {
                                it.stargazersCount ?: 0
                            }
                        )
                        searchReposResult.value = sortedList
                        searchUsersResult.value = emptyList()
                    },
                    onFailure = {
                        searchError.value = it.localizedMessage ?: "Failed to find repositories"
                    }
                )
            } else {
                val res = repository.searchUsers(query)
                res.fold(
                    onSuccess = { rawUsers ->
                        // Boost user search relevancy by exact match and login matching heuristics
                        val sortedUsers = rawUsers.sortedWith(
                            compareByDescending<GitHubUser> {
                                it.login.equals(query, ignoreCase = true)
                            }.thenByDescending {
                                it.login.startsWith(query, ignoreCase = true)
                            }.thenByDescending {
                                it.login.contains(query, ignoreCase = true)
                            }
                        )
                        searchUsersResult.value = sortedUsers
                        searchReposResult.value = emptyList()
                    },
                    onFailure = {
                        searchError.value = it.localizedMessage ?: "Failed to find users"
                    }
                )
            }
            isSearching.value = false
        }
    }

    fun clearSearch() {
        searchQuery.value = ""
        searchReposResult.value = emptyList()
        searchUsersResult.value = emptyList()
        searchError.value = null
        updateBackEnabled()
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteSearch(id)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Load PROFILE Details
    fun loadUserProfile(username: String) {
        viewModelScope.launch {
            userProfileLoading.value = true
            userProfileError.value = null
            userProfileData.value = null
            userProfileRepos.value = emptyList()

            val userRes = repository.getUser(username)
            userRes.fold(
                onSuccess = { user ->
                    userProfileData.value = user
                    // Load repositories
                    val reposRes = repository.getUserRepos(username)
                    reposRes.fold(
                        onSuccess = { repos ->
                            userProfileRepos.value = repos
                        },
                        onFailure = {
                            // Soft error for repo list
                        }
                    )
                },
                onFailure = {
                        userProfileError.value = it.localizedMessage ?: "Failed to load user profile"
                }
            )
            userProfileLoading.value = false
        }
    }

    // Load REPOSITORY Details
    fun loadRepositoryDetails(owner: String, repoName: String) {
        viewModelScope.launch {
            repoDetailLoading.value = true
            repoDetailError.value = null
            repoDetailData.value = null
            repoActiveTab.value = "CODE"
            codePath.value = ""
            codePathStack.clear()
            contentsJob?.cancel()
            codeContentsLoading.value = false
            releasesLoadedFor = null
            releasesList.value = emptyList()
            closeFileView()
            updateBackEnabled()

            val repoRes = repository.getRepository(owner, repoName)
            repoRes.fold(
                onSuccess = { repo ->
                    repoDetailData.value = repo
                    repoDetailIsBookmarked.value = repository.isBookmarked(repo.id)
                    // Fetch the tree once into the disk mirror, then navigate from it.
                    loadContents("")
                    // Trigger language retrieval
                    loadRepoLanguages(owner, repo.name)
                },
                onFailure = {
                    repoDetailError.value = it.localizedMessage ?: "Failed to load repository details"
                }
            )
            repoDetailLoading.value = false
        }
    }

    fun loadRepoLanguages(owner: String, repo: String) {
        viewModelScope.launch {
            repoLanguagesLoading.value = true
            repoLanguages.value = emptyMap()
            val res = repository.getRepoLanguages(owner, repo)
            res.fold(
                onSuccess = { repoLanguages.value = it },
                onFailure = { }
            )
            repoLanguagesLoading.value = false
        }
    }

    fun loadCommitDetail(owner: String, repo: String, commit: GitHubCommit) {
        commitDetailJob?.cancel()
        commitDetailJob = viewModelScope.launch {
            commitDetailLoading.value = true
            commitDetailError.value = null
            // Show commit metadata immediately from the list entry while the
            // patch is fetched from github.com (not api.github.com).
            activeCommitDetail.value = GitHubCommitDetail(
                sha = commit.sha,
                commit = commit.commit,
                author = commit.author,
                files = null
            )
            val res = repository.getCommitPatchFiles(owner, repo, commit.sha)
            res.fold(
                onSuccess = { files ->
                    activeCommitDetail.value = GitHubCommitDetail(
                        sha = commit.sha,
                        commit = commit.commit,
                        author = commit.author,
                        files = files
                    )
                },
                onFailure = {
                    commitDetailError.value = it.localizedMessage ?: "Failed to load commit diff"
                }
            )
            commitDetailLoading.value = false
        }
    }

    fun closeCommitDetail() {
        commitDetailJob?.cancel()
        commitDetailJob = null
        activeCommitDetail.value = null
        commitDetailError.value = null
        commitDetailLoading.value = false
    }

    // Toggle Bookmarks
    fun toggleBookmark(repo: GitHubApiRepository) {
        viewModelScope.launch {
            if (repoDetailData.value?.id == repo.id) {
                if (repoDetailIsBookmarked.value) {
                    repository.removeBookmarkById(repo.id)
                    repoDetailIsBookmarked.value = false
                } else {
                    repository.addBookmark(repo)
                    repoDetailIsBookmarked.value = true
                }
            } else {
                // If toggling from search/list, we check DB directly or blindly add/remove
                // Wait, it's easier to just provide explicit add/remove
            }
        }
    }

    fun addBookmark(repo: GitHubApiRepository) {
        viewModelScope.launch {
            repository.addBookmark(repo)
            if (repoDetailData.value?.id == repo.id) {
                repoDetailIsBookmarked.value = true
            }
        }
    }

    fun removeBookmark(repoId: Long) {
        viewModelScope.launch {
            repository.removeBookmarkById(repoId)
            if (repoDetailData.value?.id == repoId) {
                repoDetailIsBookmarked.value = false
            }
        }
    }

    // Contents directories
    fun loadContents(path: String) {
        repoDetailData.value ?: return
        // A new directory tap supersedes the previous one; cancelling stops an
        // in-flight tree read from overwriting the directory the user is on.
        contentsJob?.cancel()
        contentsJob = viewModelScope.launch {
            codeContentsLoading.value = true
            codeError.value = null
            clearReadme()
            val repo = repoDetailData.value ?: run {
                codeContentsLoading.value = false
                return@launch
            }

            // Directory listings are scanned from the tree on disk, never from
            // a whole-repo map held in RAM.
            val branch = repo.defaultBranch ?: "main"
            var listingRes = repository.getDirectoryListing(
                repo.owner.login, repo.name, branch, path
            )
            if (listingRes.isFailure && repo.defaultBranch == null) {
                // Empty/legacy repos may still use "master" instead of "main".
                listingRes = repository.getDirectoryListing(
                    repo.owner.login, repo.name, "master", path
                )
            }

            val localListing = listingRes.getOrNull()?.entries
            if (localListing != null) {
                applyContents(path, localListing)
                codeContentsLoading.value = false
                return@launch
            }

            // Path missing from the tree, tree truncated, or repo too large:
            // fall back to the per-directory contents API only for this path.
            val contentsRes = repository.getContents(repo.owner.login, repo.name, path)
            contentsRes.fold(
                onSuccess = { rawList ->
                    applyContents(path, rawList)
                },
                onFailure = {
                    codeError.value = it.localizedMessage ?: "Failed to load repo files"
                }
            )
            codeContentsLoading.value = false
        }
    }

    private fun clearReadme() {
        readmeName.value = null
        readmeContent.value = null
        readmeDownloadUrl.value = null
        readmeLoading.value = false
    }

    private fun applyContents(path: String, rawList: List<GitHubContent>) {
        val sortedList = rawList.sortedWith(compareBy({ content -> content.type != "dir" }, { content -> content.name }))
        codeContents.value = sortedList
        codePath.value = path

        // Automatically auto-detect and background load README files
        val readmeFile = sortedList.firstOrNull { file ->
            file.type == "file" && (
                file.name.equals("README.md", ignoreCase = true) ||
                file.name.equals("README", ignoreCase = true) ||
                file.name.startsWith("README.", ignoreCase = true)
            )
        }
        if (readmeFile != null) {
            readmeName.value = readmeFile.name
            loadReadmeContent(readmeFile.downloadUrl)
        }
    }

    fun loadReadmeContent(downloadUrl: String?) {
        if (downloadUrl == null) return
        readmeDownloadUrl.value = downloadUrl
        viewModelScope.launch {
            readmeLoading.value = true
            val res = repository.getRawContent(downloadUrl)
            res.fold(
                onSuccess = {
                    readmeContent.value = it
                },
                onFailure = {
                    readmeContent.value = "Failed to load README: ${it.localizedMessage}"
                }
            )
            readmeLoading.value = false
        }
    }

    fun navigateIntoDirectory(path: String) {
        codePathStack.add(codePath.value)
        loadContents(path)
        updateBackEnabled()
    }

    fun navigateUpDirectory(): Boolean {
        if (codePathStack.isNotEmpty()) {
            val parentPath = codePathStack.removeAt(codePathStack.size - 1)
            loadContents(parentPath)
            updateBackEnabled()
            return true
        }
        updateBackEnabled()
        return false
    }

    fun isAtRootDirectory(): Boolean = codePathStack.isEmpty()

    // Issues
    fun loadIssues(state: String) {
        viewModelScope.launch {
            issuesLoading.value = true
            issuesError.value = null
            issuesState.value = state
            val repo = repoDetailData.value ?: return@launch

            val res = repository.getIssues(repo.owner.login, repo.name, state)
            res.fold(
                onSuccess = {
                    issuesList.value = it
                },
                onFailure = {
                    issuesError.value = it.localizedMessage ?: "Failed to fetch issues"
                }
            )
            issuesLoading.value = false
        }
    }

    // Commits
    fun loadCommits() {
        viewModelScope.launch {
            commitsLoading.value = true
            commitsError.value = null
            val repo = repoDetailData.value ?: return@launch

            val res = repository.getCommits(repo.owner.login, repo.name)
            res.fold(
                onSuccess = {
                    commitsList.value = it
                },
                onFailure = {
                    commitsError.value = it.localizedMessage ?: "Failed to fetch commits"
                }
            )
            commitsLoading.value = false
        }
    }

    // Code Contents Viewer Loader
    fun viewFileContent(fileName: String, downloadUrl: String?) {
        if (downloadUrl == null) {
            activeViewFileError.value = "File download URL is unavailable"
            return
        }
        viewModelScope.launch {
            activeViewFileLoading.value = true
            activeViewFileError.value = null
            activeViewFileName.value = fileName
            activeViewFileContent.value = ""
            
            val res = repository.getRawContent(downloadUrl)
            res.fold(
                onSuccess = {
                    activeViewFileContent.value = it
                },
                onFailure = {
                    activeViewFileError.value = it.localizedMessage ?: "Failed to download file content"
                }
            )
            activeViewFileLoading.value = false
        }
    }

    fun closeFileView() {
        activeViewFileName.value = ""
        activeViewFileContent.value = ""
        activeViewFileError.value = null
    }

    // Releases List Loader — persistent cache first, API only when stale.
    fun loadReleases(forceRefresh: Boolean = false) {
        val repo = repoDetailData.value ?: return
        val key = repo.fullName
        if (!forceRefresh && releasesLoadedFor == key) return

        viewModelScope.launch {
            releasesLoading.value = true
            releasesError.value = null

            val res = repository.getReleases(repo.owner.login, repo.name, forceRefresh)
            res.fold(
                onSuccess = {
                    releasesList.value = it
                    releasesLoadedFor = key
                },
                onFailure = {
                    releasesError.value = it.localizedMessage ?: "Failed to fetch releases"
                }
            )
            releasesLoading.value = false
        }
    }

    fun refreshReleases() {
        loadReleases(forceRefresh = true)
    }

    // Scrape full release notes from the GitHub web page (not API).
    // Called on-demand when user taps the release notes preview. The text is
    // served from the JsonDiskCache mirror on repeat opens; only the currently
    // displayed string is held in RAM.
    private var releaseNotesJob: Job? = null

    fun scrapeReleaseNotes(htmlUrl: String) {
        releaseNotesJob?.cancel()
        releaseNotesJob = viewModelScope.launch {
            releaseNotesLoading.value = true
            // Disk mirror hit costs one small file read, no Jsoup, no network.
            val result = repository.scrapeReleaseNotes(htmlUrl)
            releaseNotesContent.value = result
            releaseNotesLoading.value = false
        }
    }

    fun clearReleaseNotes() {
        releaseNotesJob?.cancel()
        releaseNotesLoading.value = false
        releaseNotesContent.value = null
    }

    /**
     * Clear every disk/memory cache in the app while preserving bookmarks,
     * search history, tokens and settings.
     */
    fun clearAllCaches() {
        if (cacheClearing.value) return
        viewModelScope.launch {
            cacheClearing.value = true
            cachesCleared.value = false

            // Reset in-memory mirrors so stale content cannot resurface.
            releaseNotesJob?.cancel()
            releaseNotesLoading.value = false
            releaseNotesContent.value = null
            releasesLoadedFor = null
            releasesList.value = emptyList()
            releasesError.value = null
            readmeName.value = null
            readmeContent.value = null
            readmeDownloadUrl.value = null
            activeViewFileName.value = ""
            activeViewFileContent.value = ""
            activeViewFileError.value = null

            repository.clearAllCaches()

            cacheClearing.value = false
            cachesCleared.value = true
        }
    }

    // Quick direct repository search (to jump to repository directly from search or bookmarks)
    fun selectRepository(owner: String, repoName: String) {
        navigateTo(Screen.RepositoryDetail(owner, repoName))
        loadRepositoryDetails(owner, repoName)
    }

    // Scroll state persistence
    private val scrollStates = mutableMapOf<String, Pair<Int, Int>>()

    fun saveScrollState(key: String, index: Int, offset: Int) {
        scrollStates[key] = Pair(index, offset)
    }

    fun getScrollState(key: String): Pair<Int, Int> {
        return scrollStates[key] ?: Pair(0, 0)
    }
}
