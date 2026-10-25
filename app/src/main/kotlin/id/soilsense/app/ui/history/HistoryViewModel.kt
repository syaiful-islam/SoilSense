package id.soilsense.app.ui.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.soilsense.app.data.history.ExportResult
import id.soilsense.app.data.history.HistoryRepository
import id.soilsense.app.data.model.RecordedReading
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Loaded(
        val items: List<RecordedReading>,
        /** All stored rows; [items] is capped at [HistoryViewModel.DISPLAY_LIMIT]. */
        val totalCount: Int,
        val isExporting: Boolean,
    ) : HistoryUiState
}

/** One-off results shown as a snackbar. */
sealed interface HistoryEvent {
    data class Exported(val rowCount: Int) : HistoryEvent

    data object ExportFailed : HistoryEvent

    data object Cleared : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val isExporting = MutableStateFlow(false)
    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.observeLatest(DISPLAY_LIMIT),
        historyRepository.observeCount(),
        isExporting,
    ) { items, count, exporting ->
        HistoryUiState.Loaded(items = items, totalCount = count, isExporting = exporting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    fun export(destination: Uri) {
        if (isExporting.value) return
        viewModelScope.launch {
            isExporting.value = true
            val event = when (val result = historyRepository.exportCsv(destination)) {
                is ExportResult.Success -> HistoryEvent.Exported(result.rowCount)
                ExportResult.Failure -> HistoryEvent.ExportFailed
            }
            isExporting.value = false
            _events.send(event)
        }
    }

    fun clear() {
        viewModelScope.launch {
            historyRepository.clear()
            _events.send(HistoryEvent.Cleared)
        }
    }

    companion object {
        /** Enough to scroll back through a field session; the CSV export always contains everything. */
        const val DISPLAY_LIMIT: Int = 500
    }
}
