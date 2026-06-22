package com.example.data.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubService {
    @GET("users/{username}")
    suspend fun getUser(
        @Path("username") username: String,
        @Header("Authorization") auth: String? = null
    ): GitHubUser

    @GET("users/{username}/repos")
    suspend fun getUserRepos(
        @Path("username") username: String,
        @Query("sort") sort: String = "updated",
        @Header("Authorization") auth: String? = null
    ): List<GitHubApiRepository>

    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Query("per_page") perPage: Int = 100,
        @Header("Authorization") auth: String? = null
    ): SearchRepoResponse

    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 100,
        @Header("Authorization") auth: String? = null
    ): SearchUserResponse

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") auth: String? = null
    ): GitHubApiRepository

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Header("Authorization") auth: String? = null
    ): List<GitHubIssue>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") auth: String? = null
    ): List<GitHubCommit>

    @GET("repos/{owner}/{repo}/commits/{ref}")
    suspend fun getCommitDetail(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
        @Header("Authorization") auth: String? = null
    ): GitHubCommitDetail

    @GET("repos/{owner}/{repo}/languages")
    suspend fun getRepoLanguages(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") auth: String? = null
    ): Map<String, Any>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Header("Authorization") auth: String? = null
    ): List<GitHubContent>

    @GET("repos/{owner}/{repo}/contents")
    suspend fun getRootContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") auth: String? = null
    ): List<GitHubContent>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") auth: String? = null
    ): List<GitHubRelease>

    @retrofit2.http.GET
    suspend fun getRawContent(
        @retrofit2.http.Url url: String,
        @Header("Authorization") auth: String? = null
    ): okhttp3.ResponseBody
}
