package com.example.data.repository

import com.example.data.local.AnalysisDao
import com.example.data.local.SavedAnalysis
import kotlinx.coroutines.flow.Flow

class AnalysisRepository(private val analysisDao: AnalysisDao) {
    val allAnalyses: Flow<List<SavedAnalysis>> = analysisDao.getAllAnalyses()

    suspend fun getAnalysisById(id: Int): SavedAnalysis? {
        return analysisDao.getAnalysisById(id)
    }

    suspend fun getAnalysisByVideoId(videoId: String): SavedAnalysis? {
        return analysisDao.getAnalysisByVideoId(videoId)
    }

    suspend fun insert(analysis: SavedAnalysis): Long {
        return analysisDao.insertAnalysis(analysis)
    }

    suspend fun deleteById(id: Int) {
        analysisDao.deleteAnalysisById(id)
    }

    suspend fun clearHistory() {
        analysisDao.clearAll()
    }
}
