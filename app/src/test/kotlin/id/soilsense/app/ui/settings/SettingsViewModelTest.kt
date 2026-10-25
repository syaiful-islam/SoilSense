package id.soilsense.app.ui.settings

import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.sensor.SlaveIdDetection
import id.soilsense.app.testing.FakeSettingsRepository
import id.soilsense.app.testing.FakeSoilSensorRepository
import id.soilsense.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sensor = FakeSoilSensorRepository()
    private val settings = FakeSettingsRepository()
    private val viewModel = SettingsViewModel(settings, sensor)

    @Test
    fun `detected id can be applied to settings`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        sensor.detectionResult = SlaveIdDetection.Found(5)

        viewModel.detectSlaveId()
        assertEquals(DetectionState.Found(5), viewModel.uiState.value.detection)

        viewModel.applyDetectedSlaveId()
        assertEquals(5, settings.current.slaveId)
        assertEquals(DetectionState.Idle, viewModel.uiState.value.detection)
    }

    @Test
    fun `failed detection is reported`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        sensor.detectionResult = SlaveIdDetection.Failed(SensorReadError.NoResponse)

        viewModel.detectSlaveId()

        assertEquals(DetectionState.Failed(SensorReadError.NoResponse), viewModel.uiState.value.detection)
    }

    @Test
    fun `out of range slave id is ignored`() = runTest {
        viewModel.setSlaveId(0)
        viewModel.setSlaveId(248)

        assertEquals(1, settings.current.slaveId)
    }

    @Test
    fun `poll interval is clamped to the allowed range`() = runTest {
        viewModel.setPollInterval(600)

        assertEquals(60, settings.current.pollIntervalSeconds)
    }

    @Test
    fun `connection state is exposed`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        sensor.connect()

        assertEquals(true, viewModel.uiState.value.isConnected)
    }
}
