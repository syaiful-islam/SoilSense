package id.soilsense.app.data.history

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.soilsense.modbus.soil.SoilReading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingDaoTest {

    private lateinit var database: SoilSenseDatabase
    private lateinit var dao: ReadingDao

    private val reading = SoilReading(46.4, 33.2, 44, 6.8, 32, 88, 104, null, 22)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SoilSenseDatabase::class.java,
        ).build()
        dao = database.readingDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun latestIsNewestFirstAndLimited() = runTest {
        listOf(1_000L, 3_000L, 2_000L).forEach { dao.insert(reading.toEntity(it)) }

        val latest = dao.observeLatest(limit = 2).first()

        assertEquals(listOf(3_000L, 2_000L), latest.map { it.timestampMillis })
    }

    @Test
    fun roundTripPreservesNullableColumns() = runTest {
        dao.insert(reading.toEntity(1_000L))

        val stored = dao.getAllOldestFirst().single().toModel()

        assertEquals(reading, stored.reading)
    }

    @Test
    fun deleteAllEmptiesTable() = runTest {
        dao.insert(reading.toEntity(1_000L))

        dao.deleteAll()

        assertEquals(0, dao.observeCount().first())
    }
}
