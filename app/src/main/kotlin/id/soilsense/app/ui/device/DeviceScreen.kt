package id.soilsense.app.ui.device

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.soilsense.app.R
import id.soilsense.app.data.model.ConnectionFailure
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.UsbAdapterInfo
import id.soilsense.app.ui.common.label
import id.soilsense.app.ui.theme.SoilSenseTheme

@Composable
fun DeviceRoute(viewModel: DeviceViewModel = hiltViewModel()) {
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    DeviceScreen(connection = connection, onConnect = viewModel::connect, onDisconnect = viewModel::disconnect)
}

/** USB adapter detection, connect/disconnect and wiring help; kept off Home so readings get the space. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    connection: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text(stringResource(R.string.nav_device)) }, scrollBehavior = scrollBehavior)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionPanel(connection, onConnect = onConnect, onDisconnect = onDisconnect)
            WiringGuide()
        }
    }
}

private enum class Tone { NEUTRAL, GOOD, BAD }

private data class PanelContent(
    @param:StringRes val title: Int,
    val subtitle: String?,
    val tone: Tone,
    @param:StringRes val action: Int?,
    val inProgress: Boolean = false,
)

@Composable
private fun ConnectionPanel(connection: ConnectionState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val content = connection.toPanelContent()
    val (accent, onAccent) = when (content.tone) {
        Tone.GOOD -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        Tone.BAD -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        Tone.NEUTRAL -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp).clip(CircleShape).background(accent),
            ) {
                if (content.inProgress) {
                    CircularProgressIndicator(color = onAccent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                } else {
                    Icon(
                        painterResource(if (content.tone == Tone.GOOD) R.drawable.ic_check_circle else R.drawable.ic_usb),
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(content.title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content.subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            content.action?.let { action ->
                Spacer(Modifier.height(16.dp))
                if (action == R.string.action_disconnect) {
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(action)) }
                } else {
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(action)) }
                }
            }
        }
    }
}

@Composable
private fun ConnectionState.toPanelContent(): PanelContent = when (this) {
    ConnectionState.NoDevice -> PanelContent(
        R.string.connection_no_device,
        stringResource(R.string.connection_no_device_hint),
        Tone.NEUTRAL,
        R.string.action_retry,
    )
    is ConnectionState.Disconnected ->
        PanelContent(R.string.connection_ready, device.label(), Tone.NEUTRAL, R.string.action_connect)
    ConnectionState.RequestingPermission ->
        PanelContent(R.string.connection_requesting_permission, null, Tone.NEUTRAL, null, inProgress = true)
    ConnectionState.PermissionDenied -> PanelContent(
        R.string.connection_permission_denied,
        stringResource(R.string.connection_permission_denied_hint),
        Tone.BAD,
        R.string.action_request_permission,
    )
    ConnectionState.Connecting ->
        PanelContent(R.string.connection_connecting, null, Tone.NEUTRAL, null, inProgress = true)
    is ConnectionState.Connected -> PanelContent(
        R.string.connection_connected,
        stringResource(R.string.connection_connected_detail, device.label(), baudRate),
        Tone.GOOD,
        R.string.action_disconnect,
    )
    is ConnectionState.Failed -> when (reason) {
        ConnectionFailure.OPEN_FAILED -> PanelContent(
            R.string.connection_open_failed,
            stringResource(R.string.connection_open_failed_hint),
            Tone.BAD,
            R.string.action_retry,
        )
        ConnectionFailure.CONNECTION_LOST -> PanelContent(
            R.string.connection_lost,
            stringResource(R.string.connection_lost_hint),
            Tone.BAD,
            R.string.action_retry,
        )
    }
}

/** One cable of the probe, drawn in its real colour so it can be matched by eye. */
private data class Wire(
    val color: Color,
    @param:StringRes val name: Int,
    @param:StringRes val signal: Int,
    @param:StringRes val target: Int,
)

private val wires = listOf(
    Wire(Color(0xFF8D5524), R.string.wire_brown, R.string.wire_signal_power, R.string.wire_target_power),
    Wire(Color(0xFF212121), R.string.wire_black, R.string.wire_signal_ground, R.string.wire_target_ground),
    Wire(Color(0xFFFBC02D), R.string.wire_yellow, R.string.wire_signal_a, R.string.wire_target_a),
    Wire(Color(0xFF1E88E5), R.string.wire_blue, R.string.wire_signal_b, R.string.wire_target_b),
)

@Composable
private fun WiringGuide() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.wiring_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.wiring_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            wires.forEachIndexed { index, wire ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WireRow(wire)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.wiring_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WireRow(wire: Wire) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {},
    ) {
        // Outlined so the black wire stays visible in dark theme.
        Box(
            Modifier
                .size(width = 10.dp, height = 36.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(wire.color)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(wire.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(wire.signal),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                stringResource(wire.target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceConnectedPreview() {
    SoilSenseTheme(dynamicColor = false) {
        DeviceScreen(
            connection = ConnectionState.Connected(UsbAdapterInfo("CH34x", 0x1A86, 0x7523), 4800),
            onConnect = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceNoAdapterPreview() {
    SoilSenseTheme(dynamicColor = false, darkTheme = true) {
        DeviceScreen(connection = ConnectionState.NoDevice, onConnect = {}, onDisconnect = {})
    }
}
