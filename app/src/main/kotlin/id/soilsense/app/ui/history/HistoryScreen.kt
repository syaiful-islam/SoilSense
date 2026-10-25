package id.soilsense.app.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.soilsense.app.R
import id.soilsense.app.data.model.RecordedReading
import id.soilsense.app.ui.common.rememberTimeFormatter
import id.soilsense.app.ui.theme.SoilSenseTheme
import id.soilsense.modbus.soil.SoilReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is HistoryEvent.Exported ->
                    resources.getQuantityString(R.plurals.history_exported, event.rowCount, event.rowCount)
                HistoryEvent.ExportFailed -> resources.getString(R.string.history_export_failed)
                HistoryEvent.Cleared -> resources.getString(R.string.history_cleared)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) { uri ->
        uri?.let(viewModel::export)
    }

    HistoryScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onExport = { createDocument.launch(defaultExportFileName()) },
        onClear = viewModel::clear,
    )
}

private const val CSV_MIME = "text/csv"

private fun defaultExportFileName(): String =
    "soilsense-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".csv"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    snackbarHostState: SnackbarHostState,
    onExport: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val hasItems = uiState is HistoryUiState.Loaded && uiState.totalCount > 0
    val isExporting = uiState is HistoryUiState.Loaded && uiState.isExporting

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_history)) },
                actions = {
                    if (isExporting) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        }
                    } else {
                        IconButton(onClick = onExport, enabled = hasItems) {
                            Icon(painterResource(R.drawable.ic_download), stringResource(R.string.action_export_csv))
                        }
                    }
                    IconButton(onClick = { confirmClear = true }, enabled = hasItems && !isExporting) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_clear_history))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (uiState) {
                HistoryUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is HistoryUiState.Loaded ->
                    if (uiState.items.isEmpty()) EmptyHistory(Modifier.align(Alignment.Center)) else HistoryList(uiState)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryList(state: HistoryUiState.Loaded) {
    val formatTimestamp = rememberTimeFormatter("yMMMdjms")
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "summary") {
            Text(
                text = pluralStringResource(
                    R.plurals.history_summary,
                    state.totalCount,
                    state.totalCount,
                    state.items.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(state.items, key = { it.id }) { item ->
            HistoryRow(item, formatTimestamp(item.timestampMillis))
            HorizontalDivider()
        }
    }
}

@Composable
private fun HistoryRow(item: RecordedReading, timestamp: String) {
    val r = item.reading
    ListItem(
        headlineContent = { Text(timestamp) },
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        R.string.history_row_physical,
                        r.moisturePercent,
                        r.temperatureCelsius,
                        r.ph,
                        r.conductivityMicroSiemensPerCm,
                    ),
                )
                Text(
                    stringResource(R.string.history_row_npk, r.nitrogenMgPerKg, r.phosphorusMgPerKg, r.potassiumMgPerKg),
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    val reading = SoilReading(46.4, 33.2, 44, 6.8, 32, 88, 104)
    SoilSenseTheme(dynamicColor = false) {
        HistoryScreen(
            uiState = HistoryUiState.Loaded(
                items = listOf(RecordedReading(2, 1_761_390_000_000, reading), RecordedReading(1, 1_761_389_998_000, reading)),
                totalCount = 2,
                isExporting = false,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onExport = {},
            onClear = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryEmptyPreview() {
    SoilSenseTheme(dynamicColor = false) {
        HistoryScreen(
            uiState = HistoryUiState.Loaded(items = emptyList(), totalCount = 0, isExporting = false),
            snackbarHostState = remember { SnackbarHostState() },
            onExport = {},
            onClear = {},
        )
    }
}
