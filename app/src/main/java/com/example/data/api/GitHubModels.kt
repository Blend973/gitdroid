package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String,
    val id: Long,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "html_url") val htmlUrl: String?,
    val name: String?,
    val company: String?,
    val blog: String?,
    val location: String?,
    val email: String?,
    val bio: String?,
    @Json(name = "public_repos") val publicRepos: Int?,
    val followers: Int?,
    val following: Int?
)

@JsonClass(generateAdapter = true)
data class GitHubApiRepository(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val owner: GitHubUser,
    @Json(name = "html_url") val htmlUrl: String?,
    val description: String?,
    val fork: Boolean,
    @Json(name = "stargazers_count") val stargazersCount: Int?,
    @Json(name = "forks_count") val forksCount: Int?,
    val language: String?,
    @Json(name = "open_issues_count") val openIssuesCount: Int?
)

@JsonClass(generateAdapter = true)
data class GitHubIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val user: GitHubUser,
    val state: String, // "open" or "closed"
    val comments: Int?,
    @Json(name = "created_at") val createdAt: String?,
    val body: String?,
    @Json(name = "html_url") val htmlUrl: String?
)

@JsonClass(generateAdapter = true)
data class GitHubCommit(
    val sha: String,
    val commit: CommitDetail,
    val author: GitHubUser?
)

@JsonClass(generateAdapter = true)
data class GitHubCommitDetail(
    val sha: String,
    val commit: CommitDetail,
    val author: GitHubUser?,
    val files: List<GitHubCommitFile>?
)

@JsonClass(generateAdapter = true)
data class GitHubCommitFile(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?
)

@JsonClass(generateAdapter = true)
data class CommitDetail(
    val author: CommitAuthor,
    val message: String
)

@JsonClass(generateAdapter = true)
data class CommitAuthor(
    val name: String,
    val email: String,
    val date: String
)

@JsonClass(generateAdapter = true)
data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val type: String, // "file" or "dir"
    @Json(name = "download_url") val downloadUrl: String?
)

@JsonClass(generateAdapter = true)
data class SearchRepoResponse(
    @Json(name = "total_count") val totalCount: Int,
    val items: List<GitHubApiRepository>
)

@JsonClass(generateAdapter = true)
data class SearchUserResponse(
    @Json(name = "total_count") val totalCount: Int,
    val items: List<GitHubUser>
)

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    val id: Long,
    @Json(name = "tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @Json(name = "html_url") val htmlUrl: String?,
    @Json(name = "zipball_url") val zipballUrl: String?,
    @Json(name = "tarball_url") val tarballUrl: String?,
    val assets: List<GitHubReleaseAsset>
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @Json(name = "browser_download_url") val browserDownloadUrl: String
)
