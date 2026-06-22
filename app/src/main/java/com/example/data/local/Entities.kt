package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarked_repos")
data class BookmarkedRepoEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String?,
    val description: String?,
    val stargazersCount: Int,
    val language: String?
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val query: String,
    val type: String, // "REPO" or "USER"
    val timestamp: Long
)
