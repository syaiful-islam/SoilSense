package id.soilsense.app.ui.dashboard

import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSample
import id.soilsense.app.data.model.SensorSettings
import id.soilsense.app.testing.FakeHistoryRepository
import id.soilsense.app.testing.FakeSettingsRepository
import id.soilsense.app.testing.FakeSoilSensorRepository
import id.soilsense.app.testing.MainDispatcherRule
import id.soilsense.app.testing.testReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sensor = FakeSoilSensorRepository()
    private val settings = FakeSettingsRepository()
    private val history = FakeHistoryRepository()
    private val viewModel = DashboardViewModel(sensor, settings, history)

    @Test
    fun `initial state mirrors repository connection`() = runTest {
        assertEquals(sensor.connectionState.value, viewModel.uiState.value.connection)
        assertNull(viewModel.uiState.value.latestReading)
    }

    @Test
    fun `successful sample updates reading and timestamp`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        sensor.connect()

        sensor.emit(SensorSample.Success(testReading, timestampMillis = 1_000))

        val state = viewModel.uiState.value
        assertTrue(state.connection is ConnectionState.Connected)
        assertEquals(testReading, state.latestReading)
        assertEquals(1_000L, state.lastUpdatedMillis)
        assertNull(state.lastError)
    }

    @Test
    fun `failure keeps last reading and shows error until next success`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        sensor.connect()

        sensor.emit(SensorSample.Success(testReading, timestampMillis = 1_000))
        sensor.emit(SensorSample.Failure(SensorReadError.NoResponse, timestampMillis = 2_000))

        assertEquals(testReading, viewModel.uiState.value.latestReading)
        assertEquals(SensorReadError.NoResponse, viewModel.uiState.value.lastError)

        sensor.emit(SensorSample.Success(testReading, timestampMillis = 3_000))
        assertNull(viewModel.uiState.value.lastError)
        assertEquals(3_000L, viewModel.uiState.value.lastUpdatedMillis)
    }

    @Test
    fun `poll error is hidden once disconnected`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        sensor.connect()
        sensor.emit(SensorSample.Failure(SensorReadError.NoResponse, timestampMillis = 1_000))

        sensor.disconnect()

        assertNull(viewModel.uiState.value.lastError)
    }

    @Test
    fun `readings are saved only while recording is enabled`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        sensor.connect()

        sensor.emit(SensorSample.Success(testReading, timestampMillis = 1_000))
        viewModel.setRecording(true)
        sensor.emit(SensorSample.Success(testReading, timestampMillis = 2_000))
        sensor.emit(SensorSample.Failure(SensorReadError.CorruptedResponse, timestampMillis = 3_000))

        assertEquals(listOf(2_000L), history.saved.map { it.timestampMillis })
        assertTrue(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `connect delegates to repository`() = runTest {
        viewModel.connect()

        assertEquals(1, sensor.connectCalls)
    }

    @Test
    fun `poll interval comes from settings`() = runTest {
        val custom = FakeSettingsRepository(SensorSettings(pollIntervalSeconds = 7))
        val vm = DashboardViewModel(sensor, custom, history)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }

        assertEquals(7, vm.uiState.value.pollIntervalSeconds)
    }
}
