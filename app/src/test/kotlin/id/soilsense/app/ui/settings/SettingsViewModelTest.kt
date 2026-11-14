package id.soilsense.app.ui.settings

import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.sensor.ProbeChange
import id.soilsense.app.data.sensor.SlaveIdDetection
import id.soilsense.app.testing.FakeSettingsRepository
import id.soilsense.app.testing.FakeSoilSensorRepository
import id.soilsense.app.testing.MainDispatcherRule
import id.soilsense.modbus.soil.ProbeConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sensor = FakeSoilSensorRepository()
    private val settings = FakeSettingsRepository()
    // Created lazily: field initializers run before MainDispatcherRule installs the test Main dispatcher.
    private val viewModel by lazy { SettingsViewModel(settings, sensor) }

    @Test
    fun `detected id can be applied to settings`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        sensor.detectionResult = SlaveIdDetection.Found(5, 9600)

        viewModel.detectSlaveId()
        assertEquals(DetectionState.Found(5, 9600), viewModel.uiState.value.detection)

        viewModel.applyDetectedSlaveId()
        assertEquals(5, settings.current.slaveId)
        assertEquals(9600, settings.current.baudRate)
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
        viewModel.setSlaveId(255)

        assertEquals(1, settings.current.slaveId)
    }

    @Test
    fun `poll interval is clamped to the allowed range`() = runTest {
        viewModel.setPollInterval(600)

        assertEquals(60, settings.current.pollIntervalSeconds)
    }

    @Test
    fun `wallpaper colours can be switched on`() = runTest {
        viewModel.setUseWallpaperColors(true)

        assertEquals(true, settings.current.useWallpaperColors)
    }

    @Test
    fun `connection state is exposed`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        sensor.connect()

        assertEquals(true, viewModel.uiState.value.isConnected)
    }

    private suspend fun TestScope.connected() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        sensor.connect()
    }

    private val probeState get() = viewModel.uiState.value.probe

    @Test
    fun `probe configuration is read on connect and cleared on disconnect`() = runTest {
        connected()
        assertEquals(ProbeConfig(1, 4800), probeState.config)

        sensor.disconnect()
        advanceUntilIdle()
        assertNull(probeState.config)
    }

    @Test
    fun `slave id change waits for confirmation`() = runTest {
        connected()

        viewModel.proposeProbeSlaveId(2)
        assertEquals(ProbeConfigProposal.SlaveId(from = 1, to = 2), probeState.proposal)
        assertTrue(sensor.probeChanges.isEmpty())

        viewModel.confirmProbeChange()
        advanceUntilIdle()
        assertEquals(listOf("id=2"), sensor.probeChanges)
        assertEquals(ProbeConfig(2, 4800), probeState.config)
        assertEquals(ProbeConfigMessage.Verified, probeState.message)
        assertNull(probeState.proposal)
    }

    @Test
    fun `dismissed change writes nothing`() = runTest {
        connected()

        viewModel.proposeProbeBaudRate(9600)
        assertEquals(ProbeConfigProposal.BaudRate(from = 4800, to = 9600), probeState.proposal)
        viewModel.dismissProbeChange()
        advanceUntilIdle()

        assertNull(probeState.proposal)
        assertTrue(sensor.probeChanges.isEmpty())
    }

    @Test
    fun `power cycle is asked for when the probe does not answer with the new value yet`() = runTest {
        connected()
        sensor.probeChangeResult = ProbeChange.NeedsPowerCycle

        viewModel.proposeProbeBaudRate(9600)
        viewModel.confirmProbeChange()
        advanceUntilIdle()

        assertEquals(ProbeConfigMessage.NeedsPowerCycle, probeState.message)
        assertNull(probeState.config)
    }

    @Test
    fun `rejected change keeps the stored configuration`() = runTest {
        connected()
        sensor.probeChangeResult = ProbeChange.Failed(SensorReadError.DeviceException(2))

        viewModel.proposeProbeSlaveId(7)
        viewModel.confirmProbeChange()
        advanceUntilIdle()

        assertEquals(ProbeConfigMessage.WriteFailed(SensorReadError.DeviceException(2)), probeState.message)
        assertEquals(ProbeConfig(1, 4800), probeState.config)
    }

    @Test
    fun `invalid or unchanged values are not proposed`() = runTest {
        connected()

        viewModel.proposeProbeSlaveId(1)
        viewModel.proposeProbeSlaveId(0)
        viewModel.proposeProbeSlaveId(255)
        viewModel.proposeProbeBaudRate(4800)
        viewModel.proposeProbeBaudRate(19200)

        assertNull(probeState.proposal)
    }

    @Test
    fun `nothing can be proposed before the probe configuration is read`() = runTest {
        sensor.probeReadFailure = SensorReadError.NoResponse
        connected()

        assertEquals(ProbeConfigMessage.ReadFailed(SensorReadError.NoResponse), probeState.message)
        viewModel.proposeProbeSlaveId(2)
        viewModel.proposeProbeBaudRate(9600)
        assertNull(probeState.proposal)
    }
}
