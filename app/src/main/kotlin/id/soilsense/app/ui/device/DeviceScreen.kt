package id.soilsense.app.ui.device

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

/** USB adapter detection, connect/disconnect and wiring help; kept off the Live screen so readings get the space. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    connection: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_device)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionCard(connection, onConnect = onConnect, onDisconnect = onDisconnect)
            WiringHelpCard()
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
    SoilSenseTheme(dynamicColor = false) {
        DeviceScreen(connection = ConnectionState.NoDevice, onConnect = {}, onDisconnect = {})
    }
}
