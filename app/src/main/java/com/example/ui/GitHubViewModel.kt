package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubRepository
import com.example.data.api.*
import com.example.data.local.BookmarkedRepoEntity
import com.example.data.local.RecentSearchEntity
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

    // Releases list
    val releasesLoading = MutableStateFlow(false)
    val releasesList = MutableStateFlow<List<GitHubRelease>>(emptyList())
    val releasesError = MutableStateFlow<String?>(null)

    // Release notes scraping (on-demand from web page, not API)
    val releaseNotesLoading = MutableStateFlow(false)
    val releaseNotesContent = MutableStateFlow<String?>(null)

    // Proxy + UA rotation settings
    val proxyList = MutableStateFlow<List<String>>(emptyList())
    val uaRotationEnabled = MutableStateFlow(true)

    init {
        // Load persisted proxy list into the shared RoundRobinProxySelector
        val storedProxies = repository.getProxyList()
        proxyList.value = storedProxies
        pushProxiesToSelector(storedProxies)
        uaRotationEnabled.value = repository.isUARotationEnabled()
        RetrofitClient.uaRotationEnabled = uaRotationEnabled.value

        // Dynamically update back enabled state when search query changes
        viewModelScope.launch {
            searchQuery.collect {
                updateBackEnabled()
            }
        }
    }

    fun saveProxyList(proxies: List<String>) {
        proxyList.value = proxies
        repository.setProxyList(proxies)
        pushProxiesToSelector(proxies)
    }

    private fun pushProxiesToSelector(proxies: List<String>) {
        val parsed = proxies.mapNotNull { entry ->
            val cleaned = entry.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("socks5://")
                .removePrefix("socks4://")
            val colonIdx = cleaned.lastIndexOf(':')
            if (colonIdx > 0) {
                val host = cleaned.substring(0, colonIdx)
                val port = cleaned.substring(colonIdx + 1).toIntOrNull()
                if (host.isNotBlank() && port != null) Pair(host, port) else null
            } else null
        }
        RetrofitClient.proxySelector.updateProxies(parsed)
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
                        onFailure = { reposErr ->
                            // Soft error for repo list
                            Log.e("GitHubViewModel", "Repos load error: ${reposErr.message}")
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
            closeFileView()
            updateBackEnabled()

            val repoRes = repository.getRepository(owner, repoName)
            repoRes.fold(
                onSuccess = { repo ->
                    repoDetailData.value = repo
                    repoDetailIsBookmarked.value = repository.isBookmarked(repo.id)
                    // Trigger initial content scan
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
                onFailure = { Log.e("GitHubViewModel", "Languages load error: ${it.message}") }
            )
            repoLanguagesLoading.value = false
        }
    }

    fun loadCommitDetail(owner: String, repo: String, sha: String) {
        viewModelScope.launch {
            commitDetailLoading.value = true
            commitDetailError.value = null
            activeCommitDetail.value = null
            val res = repository.getCommitDetail(owner, repo, sha)
            res.fold(
                onSuccess = { activeCommitDetail.value = it },
                onFailure = { commitDetailError.value = it.localizedMessage ?: "Failed to load commit detail" }
            )
            commitDetailLoading.value = false
        }
    }

    fun closeCommitDetail() {
        activeCommitDetail.value = null
        commitDetailError.value = null
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
        viewModelScope.launch {
            codeContentsLoading.value = true
            codeError.value = null
            readmeName.value = null
            readmeContent.value = null
            readmeDownloadUrl.value = null
            readmeLoading.value = false
            val repo = repoDetailData.value ?: return@launch
            
            val contentsRes = repository.getContents(repo.owner.login, repo.name, path)
            contentsRes.fold(
                onSuccess = { rawList ->
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
                },
                onFailure = {
                    codeError.value = it.localizedMessage ?: "Failed to load repo files"
                }
            )
            codeContentsLoading.value = false
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

    // Releases List Loader
    fun loadReleases() {
        viewModelScope.launch {
            releasesLoading.value = true
            releasesError.value = null
            val repo = repoDetailData.value ?: return@launch
            
            val res = repository.getReleases(repo.owner.login, repo.name)
            res.fold(
                onSuccess = {
                    releasesList.value = it
                },
                onFailure = {
                    releasesError.value = it.localizedMessage ?: "Failed to fetch releases"
                }
            )
            releasesLoading.value = false
        }
    }

    // Scrape full release notes from the GitHub web page (not API).
    // Called on-demand when user taps the release notes preview.
    fun scrapeReleaseNotes(htmlUrl: String) {
        viewModelScope.launch {
            releaseNotesLoading.value = true
            releaseNotesContent.value = repository.scrapeReleaseNotes(htmlUrl)
            releaseNotesLoading.value = false
        }
    }

    fun clearReleaseNotes() {
        releaseNotesContent.value = null
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
