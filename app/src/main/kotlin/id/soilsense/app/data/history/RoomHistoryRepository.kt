package id.soilsense.app.data.history

import android.content.ContentResolver
import android.net.Uri
import id.soilsense.app.data.model.RecordedReading
import id.soilsense.app.di.IoDispatcher
import id.soilsense.modbus.soil.SoilReading
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val dao: ReadingDao,
    private val contentResolver: ContentResolver,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HistoryRepository {

    private val csvFormatter = CsvFormatter()

    override fun observeLatest(limit: Int): Flow<List<RecordedReading>> =
        dao.observeLatest(limit).map { rows -> rows.map(ReadingEntity::toModel) }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun save(timestampMillis: Long, reading: SoilReading) {
        dao.insert(reading.toEntity(timestampMillis))
    }

    override suspend fun clear() = dao.deleteAll()

    override suspend fun exportCsv(destination: Uri): ExportResult = withContext(ioDispatcher) {
        val rows = dao.getAllOldestFirst()
        try {
            val stream = contentResolver.openOutputStream(destination, "wt")
                ?: return@withContext ExportResult.Failure
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine(csvFormatter.header())
                rows.forEach { writer.appendLine(csvFormatter.row(it.toModel())) }
            }
            ExportResult.Success(rows.size)
        } catch (_: IOException) {
            ExportResult.Failure
        } catch (_: SecurityException) {
            // The document provider revoked access between picking and writing.
            ExportResult.Failure
        }
    }
}
