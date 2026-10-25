package id.soilsense.app.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ReadingEntity::class], version = 1, exportSchema = true)
abstract class SoilSenseDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao

    companion object {
        const val NAME = "soilsense.db"
    }
}
