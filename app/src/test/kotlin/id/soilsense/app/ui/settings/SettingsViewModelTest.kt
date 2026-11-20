package id.soilsense.app.ui.settings

import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.sensor.ProbeChange
import id.soilsense.app.data.sensor.ProbeSearch
import id.soilsense.app.testing.FakeSettingsRepository
import id.soilsense.app.testing.FakeSoilSensorRepository
import id.soilsense.app.testing.MainDispatcherRule
import id.soilsense.modbus.soil.ProbeConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
    fun `search reports progress and the found id can be applied`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val steps = MutableSharedFlow<ProbeSearch>()
        sensor.searchFlow = steps
        viewModel.detectSlaveId()

        steps.emit(ProbeSearch.Asking(4800))
        assertEquals(DetectionState.Running(baudRate = 4800), viewModel.uiState.value.detection)
        steps.emit(ProbeSearch.Scanning(slaveId = 4, baudRate = 9600))
        assertEquals(DetectionState.Running(baudRate = 9600, slaveId = 4), viewModel.uiState.value.detection)
        steps.emit(ProbeSearch.Found(slaveId = 5, baudRate = 9600))
        assertEquals(DetectionState.Found(5, 9600), viewModel.uiState.value.detection)

        viewModel.applyDetectedSlaveId()
        assertEquals(5, settings.current.slaveId)
        assertEquals(9600, settings.current.baudRate)
        assertEquals(DetectionState.Idle, viewModel.uiState.value.detection)
    }

    @Test
    fun `failed search is reported`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        sensor.searchFlow = flowOf(ProbeSearch.NotFound(SensorReadError.NoResponse))

        viewModel.detectSlaveId()

        assertEquals(DetectionState.Failed(SensorReadError.NoResponse), viewModel.uiState.value.detection)
    }

    @Test
    fun `search can be cancelled`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        var cancelled = false
        sensor.searchFlow = flow {
            emit(ProbeSearch.Scanning(slaveId = 1, baudRate = 4800))
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        viewModel.detectSlaveId()
        assertIs<DetectionState.Running>(viewModel.uiState.value.detection)

        viewModel.cancelDetection()

        assertTrue(cancelled)
        assertEquals(DetectionState.Idle, viewModel.uiState.value.detection)
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

    @Test
    fun `factory reset waits for confirmation and shows its outcome in its own card`() = runTest {
        connected()
        sensor.probeConfig = ProbeConfig(slaveId = 7, baudRate = 9600)
        viewModel.refreshProbeConfig()

        viewModel.proposeFactoryReset()
        assertEquals(ProbeConfigProposal.FactoryReset(fromSlaveId = 7, fromBaudRate = 9600), probeState.proposal)
        assertEquals(0, sensor.factoryResets)

        viewModel.confirmProbeChange()

        assertEquals(1, sensor.factoryResets)
        assertEquals(ProbeConfigMessage.Verified, probeState.factoryMessage)
        assertNull(probeState.message)
        assertEquals(ProbeConfig(1, 4800), probeState.config)
    }

    @Test
    fun `factory reset can be proposed when the probe could not be read`() = runTest {
        sensor.probeReadFailure = SensorReadError.NoResponse
        connected()

        viewModel.proposeFactoryReset()

        assertEquals(ProbeConfigProposal.FactoryReset(fromSlaveId = 1, fromBaudRate = 4800), probeState.proposal)
    }

    @Test
    fun `factory reset that needs a power cycle says so`() = runTest {
        connected()
        sensor.factoryResult = ProbeChange.NeedsPowerCycle

        viewModel.proposeFactoryReset()
        viewModel.confirmProbeChange()

        assertEquals(ProbeConfigMessage.NeedsPowerCycle, probeState.factoryMessage)
        assertNull(probeState.config)
    }

    @Test
    fun `dismissed factory reset writes nothing`() = runTest {
        connected()

        viewModel.proposeFactoryReset()
        viewModel.dismissProbeChange()

        assertNull(probeState.proposal)
        assertEquals(0, sensor.factoryResets)
    }
}
