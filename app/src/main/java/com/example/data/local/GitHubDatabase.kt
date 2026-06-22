package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BookmarkedRepoEntity::class, RecentSearchEntity::class], version = 1, exportSchema = false)
abstract class GitHubDatabase : RoomDatabase() {
    abstract fun gitHubDao(): GitHubDao

    companion object {
        @Volatile
        private var INSTANCE: GitHubDatabase? = null

        fun getDatabase(context: Context): GitHubDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GitHubDatabase::class.java,
                    "github_fast_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
