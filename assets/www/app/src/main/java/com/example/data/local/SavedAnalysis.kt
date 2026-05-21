package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_analyses")
data class SavedAnalysis(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val videoUrl: String,
    val videoId: String,
    val videoTitle: String,
    val videoAuthor: String,
    val thumbnailUrl: String,
    val summary: String,
    val transcriptJson: String, // List<TranscriptSegment> serialized
    val suggestedQuestionsJson: String, // List<String> suggestions query serialized
    val timestamp: Long = System.currentTimeMillis()
)
