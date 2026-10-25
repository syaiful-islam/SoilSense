package id.soilsense.app.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {

    @Insert
    suspend fun insert(reading: ReadingEntity): Long

    @Query("SELECT * FROM readings ORDER BY timestamp_millis DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<ReadingEntity>>

    @Query("SELECT COUNT(*) FROM readings")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM readings ORDER BY timestamp_millis ASC, id ASC")
    suspend fun getAllOldestFirst(): List<ReadingEntity>

    @Query("DELETE FROM readings")
    suspend fun deleteAll()
}
