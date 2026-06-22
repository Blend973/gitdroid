package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GitHubDao {
    @Query("SELECT * FROM bookmarked_repos ORDER BY name ASC")
    fun getAllBookmarkedRepos(): Flow<List<BookmarkedRepoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarked_repos WHERE id = :id)")
    suspend fun isBookmarked(id: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(repo: BookmarkedRepoEntity)

    @Query("DELETE FROM bookmarked_repos WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSearch(): RecentSearchEntity?

    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE localId = :id")
    suspend fun deleteSearchById(id: Long)

    @Query("DELETE FROM recent_searches WHERE localId NOT IN (SELECT localId FROM recent_searches ORDER BY timestamp DESC LIMIT 10)")
    suspend fun trimOldSearches()

    @Query("DELETE FROM recent_searches")
    suspend fun clearAllSearches()
}
