package id.soilsense.app.ui.calibration

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.soilsense.app.R
import id.soilsense.app.ui.common.toText
import id.soilsense.app.ui.theme.SoilSenseTheme
import id.soilsense.modbus.soil.CalibrationSettings
import id.soilsense.modbus.soil.FactorParameter
import id.soilsense.modbus.soil.NpkCalibration
import id.soilsense.modbus.soil.Nutrient
import id.soilsense.modbus.soil.OffsetParameter
import id.soilsense.modbus.soil.SoilReading

@Composable
fun CalibrationRoute(onOpenDevice: () -> Unit, viewModel: CalibrationViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                CalibrationEvent.Saved -> resources.getString(R.string.calibration_saved)
                is CalibrationEvent.Failed -> {
                    val text = event.error.toText()
                    if (text.code != null) resources.getString(text.title, text.code) else resources.getString(text.title)
                }
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    CalibrationScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        actions = CalibrationActions(
            onOpenDevice = onOpenDevice,
            onRefresh = viewModel::refresh,
            onSelectTarget = viewModel::selectTarget,
            onTwoPointChange = viewModel::setTwoPoint,
            onReadSample = viewModel::readSample,
            onReferenceChange = viewModel::setReference,
            onFactorChange = viewModel::setFactorInput,
            onPropose = viewModel::propose,
            onResetNpk = viewModel::requestResetNpk,
            onRestoreDefaults = viewModel::requestRestoreDefaults,
            onConfirm = viewModel::confirmProposal,
            onDismiss = viewModel::dismissProposal,
        ),
    )
}

/** Callbacks of the stateless screen, grouped to keep the signature readable. */
data class CalibrationActions(
    val onOpenDevice: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onSelectTarget: (CalibrationTarget) -> Unit = {},
    val onTwoPointChange: (Boolean) -> Unit = {},
    val onReadSample: (Int) -> Unit = {},
    val onReferenceChange: (Int, String) -> Unit = { _, _ -> },
    val onFactorChange: (String) -> Unit = {},
    val onPropose: () -> Unit = {},
    val onResetNpk: () -> Unit = {},
    val onRestoreDefaults: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    uiState: CalibrationUiState,
    snackbarHostState: SnackbarHostState,
    actions: CalibrationActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_calibration)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!uiState.isConnected) {
                NotConnectedCard(actions.onOpenDevice)
                return@Column
            }
            CalibrateCard(uiState, actions)
            StoredValuesCard(uiState.calibration, uiState.isLoading, actions.onRefresh)
            ActionCard(
                title = R.string.calibration_reset_npk_title,
                body = R.string.calibration_reset_npk_body,
                button = R.string.calibration_reset_npk_action,
                enabled = !uiState.isWriting,
                onClick = actions.onResetNpk,
            )
            ActionCard(
                title = R.string.calibration_defaults_title,
                body = R.string.calibration_defaults_body,
                button = R.string.calibration_defaults_action,
                enabled = !uiState.isWriting,
                onClick = actions.onRestoreDefaults,
                outlined = true,
            )
        }
    }

    uiState.proposal?.let { ConfirmDialog(it, uiState.isWriting, actions.onConfirm, actions.onDismiss) }
}

@Composable
private fun SectionCard(@StringRes title: Int, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                trailing()
            }
            content()
        }
    }
}

@Composable
private fun NotConnectedCard(onOpenDevice: () -> Unit) {
    SectionCard(R.string.calibration_not_connected_title) {
        Text(stringResource(R.string.calibration_not_connected_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onOpenDevice) { Text(stringResource(R.string.action_open_device)) }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CalibrateCard(uiState: CalibrationUiState, actions: CalibrationActions) {
    val target = uiState.target
    SectionCard(R.string.calibration_calibrate_title) {
        Text(
            stringResource(R.string.calibration_calibrate_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalibrationTarget.entries.forEach { option ->
                FilterChip(
                    selected = option == target,
                    onClick = { actions.onSelectTarget(option) },
                    label = { Text(stringResource(option.label)) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (target.nutrient != null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { index, twoPoint ->
                    SegmentedButton(
                        selected = uiState.twoPoint == twoPoint,
                        onClick = { actions.onTwoPointChange(twoPoint) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) {
                        Text(stringResource(if (twoPoint) R.string.calibration_two_point else R.string.calibration_one_point))
                    }
                }
            }
            Hint(if (uiState.twoPoint) R.string.calibration_two_point_hint else R.string.calibration_one_point_hint)
        }
        if (target == CalibrationTarget.PH) Hint(R.string.calibration_ph_hint)

        if (target.needsSample) {
            val points = if (target.nutrient != null && uiState.twoPoint) 2 else 1
            repeat(points) { index ->
                SampleRow(
                    index = index,
                    showNumber = points > 1,
                    target = target,
                    sample = uiState.samples[index],
                    reference = uiState.references[index],
                    isSampling = uiState.isSampling,
                    onRead = { actions.onReadSample(index) },
                    onReferenceChange = { actions.onReferenceChange(index, it) },
                )
            }
        } else {
            FactorInput(target, uiState.calibration, uiState.factorInput, actions.onFactorChange)
        }

        uiState.inputError?.let {
            Text(stringResource(it.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = actions.onPropose,
            enabled = !uiState.isWriting && uiState.calibration != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.calibration_review)) }
    }
}

@Composable
private fun Hint(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SampleRow(
    index: Int,
    showNumber: Boolean,
    target: CalibrationTarget,
    sample: SoilReading?,
    reference: String,
    isSampling: Boolean,
    onRead: () -> Unit,
    onReferenceChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showNumber) {
            Text(stringResource(R.string.calibration_sample_n, index + 1), style = MaterialTheme.typography.titleSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onRead, enabled = !isSampling) { Text(stringResource(R.string.calibration_read_sensor)) }
            Spacer(Modifier.width(12.dp))
            if (isSampling) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                val value = sample?.let { target.currentValue(it) }
                Text(
                    if (value == null) {
                        stringResource(R.string.calibration_no_sample)
                    } else {
                        stringResource(R.string.calibration_sensor_shows, formatReading(target, value))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        OutlinedTextField(
            value = reference,
            onValueChange = onReferenceChange,
            label = { Text(stringResource(R.string.calibration_reference_label)) },
            supportingText = { Text(stringResource(R.string.calibration_reference_hint)) },
            suffix = target.unit?.let { { Text(stringResource(it)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FactorInput(target: CalibrationTarget, calibration: CalibrationSettings?, value: String, onChange: (String) -> Unit) {
    val parameter = requireNotNull(target.factorParameter)
    calibration?.factors?.get(parameter)?.let { raw ->
        Text(
            stringResource(R.string.calibration_current_value, formatValue(CalibrationValue.Factor(parameter, raw))),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.calibration_new_value_label)) },
        supportingText = { Text(stringResource(parameter.rangeHint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StoredValuesCard(calibration: CalibrationSettings?, isLoading: Boolean, onRefresh: () -> Unit) {
    SectionCard(
        R.string.calibration_stored_title,
        trailing = {
            if (isLoading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.calibration_refresh))
                }
            }
        },
    ) {
        if (calibration == null) {
            Hint(R.string.calibration_not_loaded)
            return@SectionCard
        }
        CalibrationTarget.entries.forEach { target ->
            val value = target.storedValue(calibration)
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Text(stringResource(target.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(formatValue(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ActionCard(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes button: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    outlined: Boolean = false,
) {
    SectionCard(title) {
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        if (outlined) {
            OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(button)) }
        } else {
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(button)) }
        }
    }
}

@Composable
private fun ConfirmDialog(proposal: CalibrationProposal, isWriting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isWriting) onDismiss() },
        title = {
            Text(
                stringResource(
                    when (proposal) {
                        is CalibrationProposal.Change -> R.string.calibration_confirm_title
                        CalibrationProposal.ResetNpk -> R.string.calibration_reset_npk_title
                        CalibrationProposal.RestoreDefaults -> R.string.calibration_defaults_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (proposal) {
                    is CalibrationProposal.Change -> {
                        Text(stringResource(proposal.target.label), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.calibration_before, formatValue(proposal.before)))
                        Text(stringResource(R.string.calibration_after, formatValue(proposal.after)), fontWeight = FontWeight.SemiBold)
                    }
                    CalibrationProposal.ResetNpk -> Text(stringResource(R.string.calibration_reset_npk_confirm))
                    CalibrationProposal.RestoreDefaults -> Text(stringResource(R.string.calibration_defaults_confirm))
                }
                Text(
                    stringResource(R.string.calibration_permanent_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private val CalibrationTarget.label: Int
    get() = when (this) {
        CalibrationTarget.TEMPERATURE -> R.string.metric_temperature
        CalibrationTarget.MOISTURE -> R.string.metric_moisture
        CalibrationTarget.CONDUCTIVITY -> R.string.metric_short_conductivity
        CalibrationTarget.PH -> R.string.metric_ph
        CalibrationTarget.NITROGEN -> R.string.symbol_nitrogen
        CalibrationTarget.PHOSPHORUS -> R.string.symbol_phosphorus
        CalibrationTarget.POTASSIUM -> R.string.symbol_potassium
        CalibrationTarget.EC_TEMPERATURE_COEFFICIENT -> R.string.calibration_ec_temp_coefficient
        CalibrationTarget.SALINITY_FACTOR -> R.string.calibration_salinity_factor
        CalibrationTarget.TDS_FACTOR -> R.string.calibration_tds_factor
    }

private val CalibrationTarget.unit: Int?
    get() = when (this) {
        CalibrationTarget.TEMPERATURE -> R.string.unit_celsius
        CalibrationTarget.MOISTURE -> R.string.unit_percent
        CalibrationTarget.CONDUCTIVITY -> R.string.unit_us_cm
        CalibrationTarget.NITROGEN, CalibrationTarget.PHOSPHORUS, CalibrationTarget.POTASSIUM -> R.string.unit_mg_kg
        else -> null
    }

private val FactorParameter.rangeHint: Int
    get() = when (this) {
        FactorParameter.EC_TEMPERATURE_COEFFICIENT -> R.string.calibration_range_ec_coefficient
        FactorParameter.SALINITY -> R.string.calibration_range_salinity
        FactorParameter.TDS -> R.string.calibration_range_tds
    }

private val CalibrationInputError.message: Int
    get() = when (this) {
        CalibrationInputError.NOT_LOADED -> R.string.calibration_error_not_loaded
        CalibrationInputError.NEED_SAMPLE -> R.string.calibration_error_need_sample
        CalibrationInputError.INVALID_NUMBER -> R.string.calibration_error_invalid_number
        CalibrationInputError.OUT_OF_RANGE -> R.string.calibration_error_out_of_range
        CalibrationInputError.INVALID_POINTS -> R.string.calibration_error_invalid_points
    }

private fun CalibrationTarget.storedValue(calibration: CalibrationSettings): CalibrationValue {
    offsetParameter?.let { return CalibrationValue.Offset(it, calibration.offsets.getValue(it)) }
    factorParameter?.let { return CalibrationValue.Factor(it, calibration.factors.getValue(it)) }
    return CalibrationValue.Npk(calibration.npk.getValue(requireNotNull(nutrient)))
}

@Composable
private fun formatReading(target: CalibrationTarget, value: Double): String {
    val number = when (target) {
        CalibrationTarget.TEMPERATURE, CalibrationTarget.MOISTURE, CalibrationTarget.PH ->
            stringResource(R.string.value_one_decimal, value)
        else -> stringResource(R.string.value_integer, value.toInt())
    }
    return target.unit?.let { number + " " + stringResource(it) } ?: number
}

@Composable
private fun formatValue(value: CalibrationValue): String = when (value) {
    is CalibrationValue.Offset -> when (value.parameter) {
        OffsetParameter.TEMPERATURE -> stringResource(R.string.calibration_offset_celsius, value.raw * value.parameter.scale)
        OffsetParameter.MOISTURE -> stringResource(R.string.calibration_offset_percent, value.raw * value.parameter.scale)
        OffsetParameter.CONDUCTIVITY -> stringResource(R.string.calibration_offset_us_cm, value.raw)
        OffsetParameter.PH -> stringResource(R.string.calibration_offset_ph, value.raw * value.parameter.scale)
    }
    is CalibrationValue.Factor -> when (value.parameter) {
        FactorParameter.EC_TEMPERATURE_COEFFICIENT ->
            stringResource(R.string.calibration_factor_percent_per_c, value.raw * value.parameter.scale)
        FactorParameter.SALINITY, FactorParameter.TDS ->
            stringResource(R.string.calibration_factor_plain, value.raw * value.parameter.scale)
    }
    is CalibrationValue.Npk ->
        stringResource(R.string.calibration_npk_value, value.calibration.factor, value.calibration.offset)
}

private val previewCalibration = CalibrationSettings(
    offsets = mapOf(
        OffsetParameter.TEMPERATURE to -12,
        OffsetParameter.MOISTURE to 0,
        OffsetParameter.CONDUCTIVITY to 25,
        OffsetParameter.PH to -4,
    ),
    factors = FactorParameter.entries.associateWith { it.defaultRaw },
    npk = Nutrient.entries.associateWith { NpkCalibration.DEFAULT } + (Nutrient.NITROGEN to NpkCalibration(1.25f, -3)),
)

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun CalibrationPreview() {
    SoilSenseTheme(dynamicColor = false) {
        CalibrationScreen(
            uiState = CalibrationUiState(isConnected = true, calibration = previewCalibration, target = CalibrationTarget.PH),
            snackbarHostState = remember { SnackbarHostState() },
            actions = CalibrationActions(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CalibrationNotConnectedPreview() {
    SoilSenseTheme(dynamicColor = false) {
        CalibrationScreen(
            uiState = CalibrationUiState(isConnected = false),
            snackbarHostState = remember { SnackbarHostState() },
            actions = CalibrationActions(),
        )
    }
}

/** Exposed for screenshot tests. */
internal val calibrationPreviewSettings: CalibrationSettings get() = previewCalibration
