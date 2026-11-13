package id.soilsense.app.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import id.soilsense.app.ui.common.HeaderBlue
import id.soilsense.app.ui.common.HeaderGradient
import id.soilsense.app.ui.common.MetricIcon
import id.soilsense.app.ui.common.PhClass
import id.soilsense.app.ui.common.SoilMetric
import id.soilsense.app.ui.common.formatMetricValue
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
            Header(
                connection = uiState.connection,
                lastUpdatedMillis = uiState.lastUpdatedMillis,
                pollIntervalSeconds = uiState.pollIntervalSeconds,
                onOpenDevice = onOpenDevice,
            )
        },
        bottomBar = { SaveBar(canSave = uiState.latestReading != null, onSave = onSaveReading) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.connection is ConnectionState.Connected) {
                uiState.lastError?.let { ReadErrorBanner(it) }
            }
            MetricGrid(reading = uiState.latestReading, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** Gradient header drawn behind the status bar (edge-to-edge), with the link status as a pill. */
@Composable
private fun Header(
    connection: ConnectionState,
    lastUpdatedMillis: Long?,
    pollIntervalSeconds: Int,
    onOpenDevice: () -> Unit,
) {
    val connected = connection as? ConnectionState.Connected
    val formatTime = rememberTimeFormatter("jms")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(HeaderGradient),
    ) {
        // Large faint leaf as texture, purely decorative.
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.10f),
            modifier = Modifier.align(Alignment.TopEnd).size(150.dp).offset(x = 30.dp, y = (-6).dp),
        )
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
        ) {
            Text(
                stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = if (connected != null) {
                        stringResource(R.string.connection_connected_summary, connected.device.label(), connected.baudRate)
                    } else {
                        stringResource(R.string.status_not_connected)
                    },
                    dotColor = if (connected != null) Color(0xFF69F0AE) else Color(0xFFFFD54F),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (connected == null) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onOpenDevice,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HeaderBlue),
                    ) { Text(stringResource(R.string.action_open_device), fontWeight = FontWeight.SemiBold) }
                }
            }
            if (connected != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (lastUpdatedMillis == null) {
                        stringResource(R.string.status_reading)
                    } else {
                        stringResource(R.string.status_updated, formatTime(lastUpdatedMillis), pollIntervalSeconds)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, dotColor: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Tiles share the remaining height; when large fonts make them taller than the screen, the grid
 * scrolls instead of clipping.
 */
@Composable
private fun MetricGrid(reading: SoilReading?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        val rows = SoilMetric.entries.chunked(columns)
        val gap = 12.dp
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
                        MetricTile(metric, reading, Modifier.weight(1f).height(tileHeight))
                    }
                }
            }
        }
    }
}

private val MIN_TILE_HEIGHT = 116.dp
private val TileShape = RoundedCornerShape(22.dp)

@Composable
private fun MetricTile(metric: SoilMetric, reading: SoilReading?, modifier: Modifier = Modifier) {
    val value = reading?.let { formatMetricValue(metric.valueOf(it)) }
    Box(
        modifier
            .shadow(elevation = 6.dp, shape = TileShape, ambientColor = metric.dark, spotColor = metric.dark)
            .clip(TileShape)
            .background(metric.gradient)
            // Read as one phrase by TalkBack, e.g. "Temperature, 12.5, °C".
            .semantics(mergeDescendants = true) {},
    ) {
        Watermark(metric.icon, Modifier.align(Alignment.BottomEnd))
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(metric.icon)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(metric.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // In the title row, so the value keeps the full height and matches the other tiles.
                if (metric == SoilMetric.PH && reading != null) {
                    Spacer(Modifier.width(8.dp))
                    TileChip(stringResource(PhClass.of(reading.ph).label))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).fillMaxWidth().alpha(if (value == null) 0.6f else 1f),
            ) {
                BasicText(
                    text = value ?: stringResource(R.string.value_unavailable),
                    style = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        // Tabular digits so the number does not jitter as readings update.
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                    // Large, but shrinks so long values and big font scales still fit.
                    autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 46.sp),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (value != null) {
                    metric.unit?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(it),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconChip(icon: MetricIcon) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .clearAndSetSemantics {},
    ) {
        when (icon) {
            is MetricIcon.Drawable ->
                Icon(painterResource(icon.res), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            is MetricIcon.Element -> Text(
                stringResource(icon.symbol),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun Watermark(icon: MetricIcon, modifier: Modifier = Modifier) {
    val faint = Color.White.copy(alpha = 0.12f)
    Box(modifier.offset(x = 18.dp, y = 18.dp).clearAndSetSemantics {}) {
        when (icon) {
            is MetricIcon.Drawable ->
                Icon(painterResource(icon.res), contentDescription = null, tint = faint, modifier = Modifier.size(96.dp))
            is MetricIcon.Element -> Text(
                stringResource(icon.symbol),
                fontSize = 92.sp,
                fontWeight = FontWeight.Black,
                color = faint,
            )
        }
    }
}

@Composable
private fun TileChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 8.dp,
        // Keeps the field above the keyboard while typing a name.
        modifier = Modifier.imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(DashboardViewModel.MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.save_name_label)) },
                placeholder = { Text(stringResource(R.string.save_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSave) save() else focusManager.clearFocus() }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = save,
                enabled = canSave,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue, contentColor = Color.White),
                modifier = Modifier.padding(top = 6.dp).heightIn(min = 56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_save), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_save_reading), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReadErrorBanner(error: SensorReadError) {
    val text = error.toText()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp)) {
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

internal val previewReading = SoilReading(
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
