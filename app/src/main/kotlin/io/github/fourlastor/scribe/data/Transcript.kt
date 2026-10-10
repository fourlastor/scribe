package io.github.fourlastor.scribe.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A finished transcription. Only the text is kept, never the audio. */
@Entity(tableName = "transcripts")
data class Transcript(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Milliseconds since the epoch. */
    val createdAt: Long,
    val text: String,
    val language: String?,
    /** Whether [language] was detected by the model rather than chosen by the user. */
    val languageDetected: Boolean,
    val durationMs: Long?,
    /** [io.github.fourlastor.scribe.speech.EngineSpec.id] of the model that produced the text. */
    val model: String,
)

@Dao
interface TranscriptDao {

    @Query("SELECT * FROM transcripts ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Transcript>>

    /** Inserts [transcript], or replaces the one with the same id. Returns its id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(transcript: Transcript): Long

    @Delete
    suspend fun delete(transcript: Transcript)
}

@Database(entities = [Transcript::class], version = 1)
abstract class ScribeDatabase : RoomDatabase() {
    abstract fun transcripts(): TranscriptDao
}
