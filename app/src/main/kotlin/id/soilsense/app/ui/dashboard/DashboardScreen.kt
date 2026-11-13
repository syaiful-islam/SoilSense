package id.soilsense.app.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.soilsense.app.R
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.ui.common.label
import id.soilsense.app.ui.common.rememberTimeFormatter
import id.soilsense.app.ui.common.toText
import id.soilsense.app.ui.theme.SoilSenseTheme
import id.soilsense.modbus.soil.SoilReading

@Composable
fun DashboardRoute(
    onOpenDevice: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is DashboardEvent.ReadingSaved ->
                    if (event.name.isBlank()) {
                        resources.getString(R.string.dashboard_reading_saved)
                    } else {
                        resources.getString(R.string.dashboard_reading_saved_named, event.name)
                    }
                DashboardEvent.NothingToSave -> resources.getString(R.string.dashboard_nothing_to_save)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    DashboardScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSaveReading = viewModel::saveCurrentReading,
        onOpenDevice = onOpenDevice,
    )
}

/** Header blue of the reference design; white title text has 4.6:1 contrast on it. */
private val HeaderBlue = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    snackbarHostState: SnackbarHostState,
    onSaveReading: (name: String) -> Unit,
    onOpenDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = HeaderBlue,
                    titleContentColor = Color.White,
                ),
            )
        },
        bottomBar = { SaveBar(canSave = uiState.latestReading != null, onSave = onSaveReading) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusStrip(
                connection = uiState.connection,
                lastUpdatedMillis = uiState.lastUpdatedMillis,
                pollIntervalSeconds = uiState.pollIntervalSeconds,
                onOpenDevice = onOpenDevice,
            )
            if (uiState.connection is ConnectionState.Connected) {
                uiState.lastError?.let { ReadErrorBanner(it) }
            }
            MetricGrid(
                metrics = metricsOf(uiState.latestReading),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/**
 * Tiles share the remaining height like the reference layout; when large fonts make them taller
 * than the screen, the grid scrolls instead of clipping.
 */
@Composable
private fun MetricGrid(metrics: List<Metric>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        val rows = metrics.chunked(columns)
        val gap = 8.dp
        val fillHeight = if (constraints.hasBoundedHeight) (maxHeight - gap * (rows.size - 1)) / rows.size else 0.dp
        val tileHeight: Dp = maxOf(MIN_TILE_HEIGHT, fillHeight)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    // A short last row (potassium on phones) stretches across the full width.
                    row.forEach { metric ->
                        MetricTile(metric, Modifier.weight(1f).height(tileHeight))
                    }
                }
            }
        }
    }
}

private val MIN_TILE_HEIGHT = 112.dp

@Composable
private fun MetricTile(metric: Metric, modifier: Modifier = Modifier) {
    val value = metric.formattedValue() ?: stringResource(R.string.value_unavailable)
    Surface(
        color = metric.color,
        contentColor = Color.White,
        shape = RoundedCornerShape(10.dp),
        // Read as one phrase by TalkBack, e.g. "Temperature, °C, 12.5".
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                MetricIconView(metric.icon)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        stringResource(metric.label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    metric.unit?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BasicText(
                    text = value,
                    style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    maxLines = 1,
                    // Large like the reference, but shrinks so long values and big font scales still fit.
                    autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 52.sp),
                )
            }
        }
    }
}

@Composable
private fun MetricIconView(icon: MetricIcon) {
    when (icon) {
        is MetricIcon.Drawable -> Icon(painterResource(icon.res), contentDescription = null, modifier = Modifier.size(22.dp))
        // N, P and K have no good pictogram; a circled element symbol is the convention on such meters.
        is MetricIcon.Element -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .border(1.5.dp, Color.White, CircleShape)
                .clearAndSetSemantics {},
        ) {
            Text(stringResource(icon.symbol), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/** Manual save, at the very bottom: an optional name plus SAVE. Nothing is stored without this button. */
@Composable
private fun SaveBar(canSave: Boolean, onSave: (name: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val save = {
        onSave(name)
        name = ""
        focusManager.clearFocus()
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        // Keeps the field above the keyboard while typing a name.
        modifier = Modifier.imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(DashboardViewModel.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.save_name_label)) },
                placeholder = { Text(stringResource(R.string.save_name_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) save() else focusManager.clearFocus() }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = save,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White),
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_save), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_save_reading))
            }
        }
    }
}

/** One line about the link to the probe; connecting and wiring live on the Device tab. */
@Composable
private fun StatusStrip(
    connection: ConnectionState,
    lastUpdatedMillis: Long?,
    pollIntervalSeconds: Int,
    onOpenDevice: () -> Unit,
) {
    val connected = connection as? ConnectionState.Connected
    val formatTime = rememberTimeFormatter("jms")
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp),
        ) {
            Icon(
                painterResource(if (connected != null) R.drawable.ic_check_circle else R.drawable.ic_usb),
                contentDescription = null,
                tint = if (connected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                if (connected != null) {
                    Text(
                        stringResource(R.string.connection_connected_summary, connected.device.label(), connected.baudRate),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (lastUpdatedMillis == null) {
                            stringResource(R.string.status_reading)
                        } else {
                            stringResource(R.string.status_updated, formatTime(lastUpdatedMillis), pollIntervalSeconds)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.status_not_connected), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (connected == null) {
                TextButton(onClick = onOpenDevice) { Text(stringResource(R.string.action_open_device)) }
            }
        }
    }
}

@Composable
private fun ReadErrorBanner(error: SensorReadError) {
    val text = error.toText()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = if (text.code != null) stringResource(text.title, text.code) else stringResource(text.title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(text.hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Metric.formattedValue(): String? = when (val v = value) {
    null -> null
    is Double -> stringResource(R.string.value_one_decimal, v)
    else -> stringResource(R.string.value_integer, v.toInt())
}

private sealed interface MetricIcon {
    data class Drawable(@param:DrawableRes val res: Int) : MetricIcon

    data class Element(@param:StringRes val symbol: Int) : MetricIcon
}

/** One tile on the dashboard. [value] is Double (one decimal) or Int; null when not yet read. */
private data class Metric(
    val key: String,
    @param:StringRes val label: Int,
    val value: Number?,
    @param:StringRes val unit: Int?,
    val color: Color,
    val icon: MetricIcon,
)

/**
 * Tile colours follow the reference design; each is darkened just enough that white text meets
 * WCAG AA (≥ 4.5:1) in light and dark themes.
 */
private object MetricColors {
    val Temperature = Color(0xFF2E7D32) // 5.1:1
    val Moisture = Color(0xFF1565C0) // 5.8:1
    val Conductivity = Color(0xFF8E24AA) // 7.0:1
    val Ph = Color(0xFFBF360C) // 5.6:1
    val Nitrogen = Color(0xFFD81B60) // 5.0:1
    val Phosphorus = Color(0xFF0277BD) // 4.8:1
    val Potassium = Color(0xFF00838F) // 4.5:1
}

/** The 7 parameters printed on the probe's label, in the reference layout's order. */
private fun metricsOf(reading: SoilReading?): List<Metric> = listOf(
    Metric(
        "temperature",
        R.string.metric_temperature,
        reading?.temperatureCelsius,
        R.string.unit_celsius,
        MetricColors.Temperature,
        MetricIcon.Drawable(R.drawable.ic_thermostat),
    ),
    Metric(
        "moisture",
        R.string.metric_moisture,
        reading?.moisturePercent,
        R.string.unit_percent,
        MetricColors.Moisture,
        MetricIcon.Drawable(R.drawable.ic_water_drop),
    ),
    Metric(
        "conductivity",
        R.string.metric_conductivity,
        reading?.conductivityMicroSiemensPerCm,
        R.string.unit_us_cm,
        MetricColors.Conductivity,
        MetricIcon.Drawable(R.drawable.ic_bolt),
    ),
    Metric(
        "ph",
        R.string.metric_ph,
        reading?.ph,
        null,
        MetricColors.Ph,
        MetricIcon.Drawable(R.drawable.ic_science),
    ),
    Metric(
        "nitrogen",
        R.string.metric_nitrogen,
        reading?.nitrogenMgPerKg,
        R.string.unit_mg_kg,
        MetricColors.Nitrogen,
        MetricIcon.Element(R.string.symbol_nitrogen),
    ),
    Metric(
        "phosphorus",
        R.string.metric_phosphorus,
        reading?.phosphorusMgPerKg,
        R.string.unit_mg_kg,
        MetricColors.Phosphorus,
        MetricIcon.Element(R.string.symbol_phosphorus),
    ),
    Metric(
        "potassium",
        R.string.metric_potassium,
        reading?.potassiumMgPerKg,
        R.string.unit_mg_kg,
        MetricColors.Potassium,
        MetricIcon.Element(R.string.symbol_potassium),
    ),
)

private val previewReading = SoilReading(
    moisturePercent = 42.2,
    temperatureCelsius = 12.5,
    conductivityMicroSiemensPerCm = 1378,
    ph = 7.9,
    nitrogenMgPerKg = 68,
    phosphorusMgPerKg = 96,
    potassiumMgPerKg = 220,
)

private val previewAdapter = UsbAdapterInfo("CH34x", 0x1A86, 0x7523)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DashboardConnectedPreview() {
    SoilSenseTheme(dynamicColor = false) {
        DashboardScreen(
            uiState = DashboardUiState(
                connection = ConnectionState.Connected(previewAdapter, 4800),
                latestReading = previewReading,
                lastUpdatedMillis = 0L,
                pollIntervalSeconds = 2,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onSaveReading = {},
            onOpenDevice = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 840, heightDp = 600)
@Composable
private fun DashboardErrorWidePreview() {
    SoilSenseTheme(dynamicColor = false, darkTheme = true) {
        DashboardScreen(
            uiState = DashboardUiState(
                connection = ConnectionState.Connected(previewAdapter, 4800),
                latestReading = previewReading,
                lastError = SensorReadError.NoResponse,
                pollIntervalSeconds = 2,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onSaveReading = {},
            onOpenDevice = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DashboardNoDevicePreview() {
    SoilSenseTheme(dynamicColor = false) {
        DashboardScreen(
            uiState = DashboardUiState(connection = ConnectionState.NoDevice),
            snackbarHostState = remember { SnackbarHostState() },
            onSaveReading = {},
            onOpenDevice = {},
        )
    }
}
