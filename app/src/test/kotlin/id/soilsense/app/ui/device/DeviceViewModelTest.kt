package id.soilsense.app.ui.device

import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.testing.FakeSoilSensorRepository
import id.soilsense.app.testing.MainDispatcherRule
import id.soilsense.app.testing.testAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DeviceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sensor = FakeSoilSensorRepository()

    // Created lazily: field initializers run before MainDispatcherRule installs the test Main dispatcher.
    private val viewModel by lazy { DeviceViewModel(sensor) }

    @Test
    fun `connect delegates to repository and state follows`() = runTest {
        viewModel.connect()

        assertEquals(1, sensor.connectCalls)
        assertEquals(ConnectionState.Connected(testAdapter, 4800), viewModel.connection.value)
    }

    @Test
    fun `disconnect returns to the adapter-ready state`() = runTest {
        viewModel.connect()

        viewModel.disconnect()

        assertEquals(ConnectionState.Disconnected(testAdapter), viewModel.connection.value)
    }
}
