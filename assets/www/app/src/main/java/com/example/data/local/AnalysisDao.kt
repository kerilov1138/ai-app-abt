package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM saved_analyses ORDER BY timestamp DESC")
    fun getAllAnalyses(): Flow<List<SavedAnalysis>>

    @Query("SELECT * FROM saved_analyses WHERE id = :id LIMIT 1")
    suspend fun getAnalysisById(id: Int): SavedAnalysis?

    @Query("SELECT * FROM saved_analyses WHERE videoId = :videoId LIMIT 1")
    suspend fun getAnalysisByVideoId(videoId: String): SavedAnalysis?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: SavedAnalysis): Long

    @Query("DELETE FROM saved_analyses WHERE id = :id")
    suspend fun deleteAnalysisById(id: Int)

    @Query("DELETE FROM saved_analyses")
    suspend fun clearAll()
}
