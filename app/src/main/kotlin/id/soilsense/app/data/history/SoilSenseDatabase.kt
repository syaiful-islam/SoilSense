package id.soilsense.app.data.history

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReadingEntity::class], version = 2, exportSchema = true)
abstract class SoilSenseDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao

    companion object {
        const val NAME = "soilsense.db"

        /** v2 adds the user-given name of a saved reading; rows saved before it get an empty name. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE readings ADD COLUMN name TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
