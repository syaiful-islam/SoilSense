package id.soilsense.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
import id.soilsense.modbus.soil.ProbeConfig
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
        onWallpaperColorsChange = viewModel::setUseWallpaperColors,
        onDetectSlaveId = viewModel::detectSlaveId,
        onApplyDetectedSlaveId = viewModel::applyDetectedSlaveId,
        probeActions = ProbeConfigActions(
            onRefresh = viewModel::refreshProbeConfig,
            onProposeSlaveId = viewModel::proposeProbeSlaveId,
            onProposeBaudRate = viewModel::proposeProbeBaudRate,
            onConfirm = viewModel::confirmProbeChange,
            onDismiss = viewModel::dismissProbeChange,
        ),
    )
}

/** Callbacks of the "Configuration in the probe" card. */
data class ProbeConfigActions(
    val onRefresh: () -> Unit = {},
    val onProposeSlaveId: (Int) -> Unit = {},
    val onProposeBaudRate: (Int) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSlaveIdChange: (Int) -> Unit,
    onBaudRateChange: (Int) -> Unit,
    onPollIntervalChange: (Int) -> Unit,
    onWallpaperColorsChange: (Boolean) -> Unit,
    onDetectSlaveId: () -> Unit,
    onApplyDetectedSlaveId: () -> Unit,
    modifier: Modifier = Modifier,
    probeActions: ProbeConfigActions = ProbeConfigActions(),
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
            SettingsCard(R.string.settings_section_communication) {
                SlaveIdField(currentSlaveId = settings.slaveId, onSave = onSlaveIdChange)
                BaudRateSelector(selected = settings.baudRate, onSelect = onBaudRateChange)
                PollIntervalSlider(seconds = settings.pollIntervalSeconds, onChange = onPollIntervalChange)
            }
            ProbeConfigCard(isConnected = uiState.isConnected, state = uiState.probe, actions = probeActions)
            SettingsCard(R.string.settings_section_detect) {
                DetectSlaveId(
                    isConnected = uiState.isConnected,
                    detection = uiState.detection,
                    onDetect = onDetectSlaveId,
                    onApply = onApplyDetectedSlaveId,
                )
            }
            SettingsCard(R.string.settings_section_appearance) {
                WallpaperColorsSwitch(checked = settings.useWallpaperColors, onCheckedChange = onWallpaperColorsChange)
            }
        }
    }

    uiState.probe.proposal?.let { ProbeChangeDialog(it, uiState.probe.isWriting, probeActions.onConfirm, probeActions.onDismiss) }
}

@Composable
private fun WallpaperColorsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is the touch target and is announced once as a switch.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_wallpaper_colors), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_wallpaper_colors_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A titled, rounded group of related settings. */
@Composable
private fun SettingsCard(
    @StringRes titleRes: Int,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(titleRes, Modifier.weight(1f))
                trailing()
            }
            content()
        }
    }
}

@Composable
private fun SectionTitle(@StringRes titleRes: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProbeConfigCard(isConnected: Boolean, state: ProbeConfigState, actions: ProbeConfigActions) {
    val config = state.config
    val busy = state.isLoading || state.isWriting
    SettingsCard(
        R.string.settings_section_probe,
        trailing = {
            if (busy) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else if (isConnected) {
                IconButton(onClick = actions.onRefresh) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.settings_probe_read))
                }
            }
        },
    ) {
        Text(
            stringResource(R.string.settings_probe_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isConnected) {
            Text(stringResource(R.string.settings_detect_requires_connection), style = MaterialTheme.typography.bodyMedium)
            return@SettingsCard
        }
        StoredInProbe(config)
        state.message?.let { ProbeMessage(it) }
        if (config == null) return@SettingsCard

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NewSlaveIdRow(current = config.slaveId, enabled = !busy, onPropose = actions.onProposeSlaveId)
        NewBaudRateRow(current = config.baudRate, enabled = !busy, onPropose = actions.onProposeBaudRate)
    }
}

@Composable
private fun StoredInProbe(config: ProbeConfig?) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_probe_stored), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            if (config == null) stringResource(R.string.settings_probe_unknown) else probeConfigLabel(config),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun probeConfigLabel(config: ProbeConfig): String =
    config.baudRate?.let { stringResource(R.string.settings_probe_config, config.slaveId, it) }
        ?: stringResource(R.string.settings_probe_config_unknown_baud, config.slaveId)

@Composable
private fun ProbeMessage(message: ProbeConfigMessage) {
    val (icon, color, text) = when (message) {
        ProbeConfigMessage.Verified ->
            Triple(R.drawable.ic_check_circle, MaterialTheme.colorScheme.primary, stringResource(R.string.settings_probe_verified))
        ProbeConfigMessage.NeedsPowerCycle ->
            Triple(R.drawable.ic_warning, MaterialTheme.colorScheme.tertiary, stringResource(R.string.settings_probe_power_cycle))
        is ProbeConfigMessage.WriteFailed ->
            Triple(R.drawable.ic_warning, MaterialTheme.colorScheme.error, stringResource(R.string.settings_probe_write_failed, errorText(message.error)))
        is ProbeConfigMessage.ReadFailed ->
            Triple(R.drawable.ic_warning, MaterialTheme.colorScheme.error, stringResource(R.string.settings_probe_read_failed, errorText(message.error)))
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun errorText(error: SensorReadError): String {
    val text = error.toText()
    return if (text.code != null) stringResource(text.title, text.code) else stringResource(text.title)
}

@Composable
private fun NewSlaveIdRow(current: Int, enabled: Boolean, onPropose: (Int) -> Unit) {
    var text by rememberSaveable(current) { mutableStateOf("") }
    val newId = text.toIntOrNull()?.takeIf { it in SensorSettings.SLAVE_ID_RANGE && it != current }
    val focusManager = LocalFocusManager.current
    val propose = {
        newId?.let {
            focusManager.clearFocus()
            onPropose(it)
        }
        Unit
    }
    Row(verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = text,
            onValueChange = { input -> text = input.filter(Char::isDigit).take(3) },
            label = { Text(stringResource(R.string.settings_probe_new_id)) },
            supportingText = {
                Text(stringResource(R.string.settings_slave_id_hint, SensorSettings.SLAVE_ID_RANGE.first, SensorSettings.SLAVE_ID_RANGE.last))
            },
            isError = text.isNotEmpty() && newId == null,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { propose() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Button(onClick = propose, enabled = enabled && newId != null, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.settings_probe_change))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewBaudRateRow(current: Int?, enabled: Boolean, onPropose: (Int) -> Unit) {
    val options = SoilSensorRegisters.SUPPORTED_BAUD_RATES
    var selected by rememberSaveable(current) { mutableStateOf(current) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_probe_new_baud), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                options.forEachIndexed { index, baud ->
                    SegmentedButton(
                        selected = baud == selected,
                        onClick = { selected = baud },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        icon = {},
                    ) { Text(baud.toString()) }
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { selected?.let(onPropose) },
                enabled = enabled && selected != null && selected != current,
            ) { Text(stringResource(R.string.settings_probe_change)) }
        }
    }
}

@Composable
private fun ProbeChangeDialog(proposal: ProbeConfigProposal, isWriting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, change) = when (proposal) {
        is ProbeConfigProposal.SlaveId ->
            R.string.settings_probe_confirm_id_title to stringResource(R.string.settings_probe_confirm_id, proposal.from, proposal.to)
        is ProbeConfigProposal.BaudRate ->
            R.string.settings_probe_confirm_baud_title to stringResource(R.string.settings_probe_confirm_baud, proposal.from, proposal.to)
    }
    AlertDialog(
        onDismissRequest = { if (!isWriting) onDismiss() },
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(change, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_probe_confirm_note), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isWriting) {
                if (isWriting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.calibration_write))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isWriting) { Text(stringResource(R.string.action_cancel)) } },
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
            // Continuous track (no 59 tick dots); the value is rounded to whole seconds.
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
                    stringResource(R.string.settings_detect_found, detection.slaveId, detection.baudRate),
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
            onWallpaperColorsChange = {},
            onDetectSlaveId = {},
            onApplyDetectedSlaveId = {},
        )
    }
}
