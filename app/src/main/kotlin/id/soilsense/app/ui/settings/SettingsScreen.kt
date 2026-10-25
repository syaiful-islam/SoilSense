package id.soilsense.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.soilsense.app.R
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSettings
import id.soilsense.app.ui.common.toText
import id.soilsense.app.ui.theme.SoilSenseTheme
import id.soilsense.modbus.soil.SoilSensorRegisters
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onSlaveIdChange = viewModel::setSlaveId,
        onBaudRateChange = viewModel::setBaudRate,
        onPollIntervalChange = viewModel::setPollInterval,
        onDetectSlaveId = viewModel::detectSlaveId,
        onApplyDetectedSlaveId = viewModel::applyDetectedSlaveId,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSlaveIdChange: (Int) -> Unit,
    onBaudRateChange: (Int) -> Unit,
    onPollIntervalChange: (Int) -> Unit,
    onDetectSlaveId: () -> Unit,
    onApplyDetectedSlaveId: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { innerPadding ->
        val settings = uiState.settings
        if (settings == null) {
            CircularProgressIndicator(Modifier.padding(innerPadding).padding(24.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(R.string.settings_section_communication)
            SlaveIdField(currentSlaveId = settings.slaveId, onSave = onSlaveIdChange)
            BaudRateSelector(selected = settings.baudRate, onSelect = onBaudRateChange)
            PollIntervalSlider(seconds = settings.pollIntervalSeconds, onChange = onPollIntervalChange)

            SectionTitle(R.string.settings_section_detect)
            DetectSlaveId(
                isConnected = uiState.isConnected,
                detection = uiState.detection,
                onDetect = onDetectSlaveId,
                onApply = onApplyDetectedSlaveId,
            )

            SectionTitle(R.string.wiring_title)
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.wiring_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun SlaveIdField(currentSlaveId: Int, onSave: (Int) -> Unit) {
    // Re-seeded whenever the stored value changes (e.g. after "use detected ID").
    var text by rememberSaveable(currentSlaveId) { mutableStateOf(currentSlaveId.toString()) }
    val validId = text.toIntOrNull()?.takeIf { it in SensorSettings.SLAVE_ID_RANGE }
    val isValid = validId != null
    val canSave = validId != null && validId != currentSlaveId
    val focusManager = LocalFocusManager.current
    val save = {
        validId?.takeIf { it != currentSlaveId }?.let { id ->
            onSave(id)
            focusManager.clearFocus()
        }
        Unit
    }

    Row(verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = text,
            onValueChange = { input -> text = input.filter(Char::isDigit).take(3) },
            label = { Text(stringResource(R.string.settings_slave_id)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.settings_slave_id_hint,
                        SensorSettings.SLAVE_ID_RANGE.first,
                        SensorSettings.SLAVE_ID_RANGE.last,
                    ),
                )
            },
            isError = !isValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Button(onClick = save, enabled = canSave, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaudRateSelector(selected: Int, onSelect: (Int) -> Unit) {
    val options = SoilSensorRegisters.SUPPORTED_BAUD_RATES
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_baud_rate), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, baud ->
                SegmentedButton(
                    selected = baud == selected,
                    onClick = { onSelect(baud) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(baud.toString())
                }
            }
        }
        Text(
            stringResource(R.string.settings_baud_rate_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PollIntervalSlider(seconds: Int, onChange: (Int) -> Unit) {
    val range = SensorSettings.POLL_INTERVAL_RANGE_SECONDS
    // Local while dragging; persisted once on release to avoid a DataStore write per frame.
    var dragValue by rememberSaveable(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    val shown = dragValue.roundToInt()
    val label = stringResource(R.string.settings_poll_interval_value, shown)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.settings_poll_interval), style = MaterialTheme.typography.bodyLarge)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onChange(dragValue.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            modifier = Modifier.semantics { stateDescription = label },
        )
    }
}

@Composable
private fun DetectSlaveId(
    isConnected: Boolean,
    detection: DetectionState,
    onDetect: () -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_detect_body), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onDetect, enabled = isConnected && detection != DetectionState.Running) {
                Text(stringResource(R.string.action_detect))
            }
            Spacer(Modifier.width(12.dp))
            if (detection == DetectionState.Running) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            }
        }
        if (!isConnected) {
            Text(
                stringResource(R.string.settings_detect_requires_connection),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (detection) {
            is DetectionState.Found -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_detect_found, detection.slaveId),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onApply) { Text(stringResource(R.string.action_use_detected_id)) }
            }
            is DetectionState.Failed -> {
                val text = detection.error.toText()
                Text(
                    if (text.code != null) stringResource(text.title, text.code) else stringResource(text.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            DetectionState.Idle, DetectionState.Running -> Unit
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SoilSenseTheme(dynamicColor = false) {
        SettingsScreen(
            uiState = SettingsUiState(
                settings = SensorSettings(),
                isConnected = true,
                detection = DetectionState.Failed(SensorReadError.NoResponse),
            ),
            onSlaveIdChange = {},
            onBaudRateChange = {},
            onPollIntervalChange = {},
            onDetectSlaveId = {},
            onApplyDetectedSlaveId = {},
        )
    }
}
