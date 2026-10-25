package id.soilsense.app.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
fun DashboardRoute(viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        uiState = uiState,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onRecordingChange = viewModel::setRecording,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRecordingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        val isConnected = uiState.connection is ConnectionState.Connected
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            fullWidthItem("connection") {
                ConnectionCard(uiState.connection, onConnect = onConnect, onDisconnect = onDisconnect)
            }
            if (isConnected) {
                fullWidthItem("status") {
                    PollStatus(uiState.lastError, uiState.lastUpdatedMillis, uiState.pollIntervalSeconds)
                }
            }
            if (uiState.connection is ConnectionState.NoDevice) {
                fullWidthItem("wiring") { WiringHelpCard() }
            }
            fullWidthItem("recording") {
                RecordingToggle(checked = uiState.isRecording, onCheckedChange = onRecordingChange)
            }
            fullWidthItem("metrics_header") {
                Text(
                    text = stringResource(R.string.dashboard_readings_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            items(metricsOf(uiState.latestReading), key = { it.key }) { metric ->
                MetricTile(metric)
            }
        }
    }
}

private fun LazyGridScope.fullWidthItem(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun PollStatus(lastError: SensorReadError?, lastUpdatedMillis: Long?, pollIntervalSeconds: Int) {
    if (lastError != null) {
        val text = lastError.toText()
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp)) {
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
        return
    }
    val formatTime = rememberTimeFormatter("jms")
    val status = if (lastUpdatedMillis == null) {
        stringResource(R.string.status_reading)
    } else {
        stringResource(R.string.status_updated, formatTime(lastUpdatedMillis), pollIntervalSeconds)
    }
    Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WiringHelpCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun RecordingToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.recording_title)) },
        supportingContent = { Text(stringResource(R.string.recording_hint)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        // The whole row is the touch target and is announced once as a switch.
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
private fun MetricTile(metric: Metric) {
    val value = metric.formattedValue()
    val unit = metric.unit?.let { stringResource(it) }
    val label = stringResource(metric.label)
    val unavailable = stringResource(R.string.value_unavailable)
    Card(
        colors = CardDefaults.cardColors(containerColor = metric.color, contentColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            // Read as one phrase by TalkBack, e.g. "Soil moisture, %, 46.4".
            .semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = if (unit != null) "$label  $unit" else label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value ?: unavailable,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One fixed colour per parameter so readings are recognisable at a glance in the field.
 * All shades keep white text at WCAG AA contrast (≥ 4.5:1) in light and dark themes.
 */
private object MetricColors {
    val Temperature = Color(0xFF2E7D32)
    val Moisture = Color(0xFF1565C0)
    val Conductivity = Color(0xFF6A1B9A)
    val Ph = Color(0xFFBF360C)
    val Nitrogen = Color(0xFFC2185B)
    val Phosphorus = Color(0xFF00695C)
    val Potassium = Color(0xFF283593)
}

@Composable
private fun Metric.formattedValue(): String? = when (val v = value) {
    null -> null
    is Double -> stringResource(R.string.value_one_decimal, v)
    else -> stringResource(R.string.value_integer, v.toInt())
}

/** One tile on the dashboard. [value] is Double (one decimal) or Int; null when not yet read. */
private data class Metric(
    val key: String,
    @param:StringRes val label: Int,
    val value: Number?,
    @param:StringRes val unit: Int?,
    val color: Color,
)

private fun metricsOf(reading: SoilReading?): List<Metric> = listOf(
    Metric(
        "temperature",
        R.string.metric_temperature,
        reading?.temperatureCelsius,
        R.string.unit_celsius,
        MetricColors.Temperature,
    ),
    Metric("moisture", R.string.metric_moisture, reading?.moisturePercent, R.string.unit_percent, MetricColors.Moisture),
    Metric(
        "conductivity",
        R.string.metric_conductivity,
        reading?.conductivityMicroSiemensPerCm,
        R.string.unit_us_cm,
        MetricColors.Conductivity,
    ),
    Metric("ph", R.string.metric_ph, reading?.ph, null, MetricColors.Ph),
    Metric("nitrogen", R.string.metric_nitrogen, reading?.nitrogenMgPerKg, R.string.unit_mg_kg, MetricColors.Nitrogen),
    Metric(
        "phosphorus",
        R.string.metric_phosphorus,
        reading?.phosphorusMgPerKg,
        R.string.unit_mg_kg,
        MetricColors.Phosphorus,
    ),
    Metric(
        "potassium",
        R.string.metric_potassium,
        reading?.potassiumMgPerKg,
        R.string.unit_mg_kg,
        MetricColors.Potassium,
    ),
)

private val previewReading = SoilReading(
    moisturePercent = 46.4,
    temperatureCelsius = 33.2,
    conductivityMicroSiemensPerCm = 44,
    ph = 6.8,
    nitrogenMgPerKg = 32,
    phosphorusMgPerKg = 88,
    potassiumMgPerKg = 104,
)

@Preview(showBackground = true)
@Composable
private fun DashboardConnectedPreview() {
    SoilSenseTheme(dynamicColor = false) {
        DashboardScreen(
            uiState = DashboardUiState(
                connection = ConnectionState.Connected(UsbAdapterInfo("CH34x", 0x1A86, 0x7523), 4800),
                latestReading = previewReading,
                lastUpdatedMillis = 0L,
                isRecording = true,
                pollIntervalSeconds = 2,
            ),
            onConnect = {},
            onDisconnect = {},
            onRecordingChange = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 840)
@Composable
private fun DashboardErrorWidePreview() {
    SoilSenseTheme(dynamicColor = false, darkTheme = true) {
        DashboardScreen(
            uiState = DashboardUiState(
                connection = ConnectionState.Connected(UsbAdapterInfo("CH34x", 0x1A86, 0x7523), 4800),
                latestReading = previewReading,
                lastError = SensorReadError.NoResponse,
                pollIntervalSeconds = 2,
            ),
            onConnect = {},
            onDisconnect = {},
            onRecordingChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardNoDevicePreview() {
    SoilSenseTheme(dynamicColor = false) {
        DashboardScreen(
            uiState = DashboardUiState(connection = ConnectionState.NoDevice),
            onConnect = {},
            onDisconnect = {},
            onRecordingChange = {},
        )
    }
}
