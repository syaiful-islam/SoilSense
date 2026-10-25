package id.soilsense.app.data.history

import android.net.Uri
import id.soilsense.app.data.model.RecordedReading
import id.soilsense.modbus.soil.SoilReading
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {

    fun observeLatest(limit: Int): Flow<List<RecordedReading>>

    fun observeCount(): Flow<Int>

    suspend fun save(timestampMillis: Long, reading: SoilReading)

    suspend fun clear()

    /** Writes every stored reading as CSV to [destination]; returns the number of rows written. */
    suspend fun exportCsv(destination: Uri): ExportResult
}

sealed interface ExportResult {
    data class Success(val rowCount: Int) : ExportResult

    data object Failure : ExportResult
}
