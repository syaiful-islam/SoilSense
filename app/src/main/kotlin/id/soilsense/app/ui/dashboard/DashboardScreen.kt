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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.soilsense.app.R
import id.soilsense.app.data.model.ConnectionFailure
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.ui.common.rememberTimeFormatter
import id.soilsense.app.ui.common.toText
import id.soilsense.app.ui.theme.SoilSenseTheme
import id.soilsense.modbus.soil.SoilReading

@Composable
fun DashboardRoute(
    onOpenData: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                DashboardEvent.ReadingSaved -> resources.getString(R.string.dashboard_reading_saved)
                DashboardEvent.NothingToSave -> resources.getString(R.string.dashboard_nothing_to_save)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    DashboardScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onSaveReading = viewModel::saveCurrentReading,
        onOpenData = onOpenData,
    )
}

/** Header blue of the reference design; white title text has 4.6:1 contrast on it. */
private val HeaderBlue = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    snackbarHostState: SnackbarHostState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSaveReading: () -> Unit,
    onOpenData: () -> Unit,
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
        bottomBar = {
            DashboardActions(
                canSave = uiState.latestReading != null,
                onSaveReading = onSaveReading,
                onOpenData = onOpenData,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val connection = uiState.connection) {
                is ConnectionState.Connected -> {
                    ConnectedStrip(
                        connection = connection,
                        isRecording = uiState.isRecording,
                        lastUpdatedMillis = uiState.lastUpdatedMillis,
                        pollIntervalSeconds = uiState.pollIntervalSeconds,
                        onDisconnect = onDisconnect,
                    )
                    uiState.lastError?.let { ReadErrorBanner(it) }
                }
                else -> {
                    ConnectionCard(connection, onConnect = onConnect, onDisconnect = onDisconnect)
                    if (connection is ConnectionState.NoDevice) WiringHelpCard()
                }
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

@Composable
private fun DashboardActions(canSave: Boolean, onSaveReading: () -> Unit, onOpenData: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSaveReading,
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_save), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_save_reading))
                }
                FilledTonalButton(onClick = onOpenData, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_history), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_open_data))
                }
            }
            Text(
                stringResource(R.string.dashboard_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** Compact connection status while connected, so the tiles get most of the screen. */
@Composable
private fun ConnectedStrip(
    connection: ConnectionState.Connected,
    isRecording: Boolean,
    lastUpdatedMillis: Long?,
    pollIntervalSeconds: Int,
    onDisconnect: () -> Unit,
) {
    val formatTime = rememberTimeFormatter("jms")
    val updated = if (lastUpdatedMillis == null) {
        stringResource(R.string.status_reading)
    } else {
        stringResource(R.string.status_updated, formatTime(lastUpdatedMillis), pollIntervalSeconds)
    }
    val status = if (isRecording) stringResource(R.string.status_with_recording, updated) else updated
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.connection_connected_summary,
                        connection.device.label(),
                        connection.baudRate,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDisconnect) { Text(stringResource(R.string.action_disconnect)) }
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
private fun ConnectionCard(
    connection: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val content = connection.toCardContent()
    val containerColor = when (connection) {
        is ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
        is ConnectionState.Failed, ConnectionState.PermissionDenied -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(colors = CardDefaults.cardColors(containerColor = containerColor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (content.inProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    Icon(painterResource(content.icon), contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(content.title), style = MaterialTheme.typography.titleMedium)
                    content.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            content.action?.let { action ->
                if (action == CardAction.DISCONNECT) {
                    OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.action_disconnect)) }
                } else {
                    Button(onClick = onConnect) { Text(stringResource(action.label)) }
                }
            }
        }
    }
}

private enum class CardAction(@param:StringRes val label: Int) {
    CONNECT(R.string.action_connect),
    RETRY(R.string.action_retry),
    REQUEST_PERMISSION(R.string.action_request_permission),
    DISCONNECT(R.string.action_disconnect),
}

private data class CardContent(
    @param:StringRes val title: Int,
    val subtitle: String?,
    @param:DrawableRes val icon: Int,
    val action: CardAction?,
    val inProgress: Boolean = false,
)

@Composable
private fun ConnectionState.toCardContent(): CardContent = when (this) {
    ConnectionState.NoDevice -> CardContent(
        title = R.string.connection_no_device,
        subtitle = stringResource(R.string.connection_no_device_hint),
        icon = R.drawable.ic_info,
        action = CardAction.RETRY,
    )
    is ConnectionState.Disconnected -> CardContent(
        title = R.string.connection_ready,
        subtitle = device.label(),
        icon = R.drawable.ic_info,
        action = CardAction.CONNECT,
    )
    ConnectionState.RequestingPermission -> CardContent(
        title = R.string.connection_requesting_permission,
        subtitle = null,
        icon = R.drawable.ic_info,
        action = null,
        inProgress = true,
    )
    ConnectionState.PermissionDenied -> CardContent(
        title = R.string.connection_permission_denied,
        subtitle = stringResource(R.string.connection_permission_denied_hint),
        icon = R.drawable.ic_warning,
        action = CardAction.REQUEST_PERMISSION,
    )
    ConnectionState.Connecting -> CardContent(
        title = R.string.connection_connecting,
        subtitle = null,
        icon = R.drawable.ic_info,
        action = null,
        inProgress = true,
    )
    is ConnectionState.Connected -> CardContent(
        title = R.string.connection_connected,
        subtitle = stringResource(R.string.connection_connected_detail, device.label(), baudRate),
        icon = R.drawable.ic_check_circle,
        action = CardAction.DISCONNECT,
    )
    is ConnectionState.Failed -> when (reason) {
        ConnectionFailure.OPEN_FAILED -> CardContent(
            title = R.string.connection_open_failed,
            subtitle = stringResource(R.string.connection_open_failed_hint),
            icon = R.drawable.ic_warning,
            action = CardAction.RETRY,
        )
        ConnectionFailure.CONNECTION_LOST -> CardContent(
            title = R.string.connection_lost,
            subtitle = stringResource(R.string.connection_lost_hint),
            icon = R.drawable.ic_warning,
            action = CardAction.RETRY,
        )
    }
}

@Composable
private fun UsbAdapterInfo.label(): String = stringResource(R.string.adapter_label, chipName, usbId)

@Composable
private fun WiringHelpCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.wiring_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.wiring_body), style = MaterialTheme.typography.bodyMedium)
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
                isRecording = true,
                pollIntervalSeconds = 2,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onConnect = {},
            onDisconnect = {},
            onSaveReading = {},
            onOpenData = {},
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
            onConnect = {},
            onDisconnect = {},
            onSaveReading = {},
            onOpenData = {},
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
            onConnect = {},
            onDisconnect = {},
            onSaveReading = {},
            onOpenData = {},
        )
    }
}
