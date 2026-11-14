package id.soilsense.app.ui.calibration

import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSample
import id.soilsense.app.testing.FakeSoilSensorRepository
import id.soilsense.app.testing.MainDispatcherRule
import id.soilsense.app.testing.factoryCalibration
import id.soilsense.app.testing.testReading
import id.soilsense.modbus.soil.CalibrationWrite
import id.soilsense.modbus.soil.FactorParameter
import id.soilsense.modbus.soil.NpkCalibration
import id.soilsense.modbus.soil.Nutrient
import id.soilsense.modbus.soil.OffsetParameter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sensor = FakeSoilSensorRepository()

    // Created lazily: field initializers run before MainDispatcherRule installs the test Main dispatcher.
    private val viewModel by lazy { CalibrationViewModel(sensor) }

    private suspend fun connected() {
        sensor.connect()
    }

    @Test
    fun `calibration is loaded when the probe connects`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        connected()

        assertEquals(factoryCalibration, viewModel.uiState.value.calibration)
        assertTrue(viewModel.uiState.value.isConnected)
    }

    @Test
    fun `nothing is written without confirmation`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        connected()
        viewModel.selectTarget(CalibrationTarget.PH)
        viewModel.readSample(0)
        viewModel.setReference(0, "8,5") // decimal comma accepted

        viewModel.propose()

        val proposal = assertIs<CalibrationProposal.Change>(viewModel.uiState.value.proposal)
        // testReading has pH 9.0; reference 8.5 → offset −5 (0.1 pH steps).
        assertEquals(CalibrationWrite.Offset(OffsetParameter.PH, -5), proposal.write)
        assertTrue(sensor.appliedWrites.isEmpty())
    }

    @Test
    fun `confirming writes, reloads and reports success`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val events = mutableListOf<CalibrationEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        connected()
        viewModel.selectTarget(CalibrationTarget.TEMPERATURE)
        viewModel.readSample(0)
        viewModel.setReference(0, "32.0")
        viewModel.propose()

        viewModel.confirmProposal()

        assertEquals(listOf<CalibrationWrite>(CalibrationWrite.Offset(OffsetParameter.TEMPERATURE, -12)), sensor.appliedWrites)
        assertEquals(-12, viewModel.uiState.value.calibration?.offsets?.get(OffsetParameter.TEMPERATURE))
        assertNull(viewModel.uiState.value.proposal)
        assertEquals(listOf<CalibrationEvent>(CalibrationEvent.Saved), events)
    }

    @Test
    fun `dismissing a proposal writes nothing`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        connected()
        viewModel.requestResetNpk()

        viewModel.dismissProposal()

        assertNull(viewModel.uiState.value.proposal)
        assertTrue(sensor.appliedWrites.isEmpty())
    }

    @Test
    fun `reset NPK and restore defaults go through confirmation`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        connected()

        viewModel.requestResetNpk()
        viewModel.confirmProposal()
        viewModel.requestRestoreDefaults()
        viewModel.confirmProposal()

        assertEquals(listOf(CalibrationWrite.ResetNpk, CalibrationWrite.RestoreDefaults), sensor.appliedWrites)
    }

    @Test
    fun `npk one point shifts the offset`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        connected()
        viewModel.selectTarget(CalibrationTarget.NITROGEN)
        viewModel.readSample(0) // N = 32
        viewModel.setReference(0, "40")

        viewModel.propose()

        val proposal = assertIs<CalibrationProposal.Change>(viewModel.uiState.value.proposal)
        assertEquals(CalibrationWrite.Npk(Nutrient.NITROGEN, NpkCalibration(1.0f, 8)), proposal.write)
    }

    @Test
    fun `npk two point needs both samples and fits factor and offset`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        connected()
        viewModel.selectTarget(CalibrationTarget.POTASSIUM)
        viewModel.setTwoPoint(true)
        sensor.nextReading = SensorSample.Success(testReading.copy(potassiumMgPerKg = 40), 0)
        viewModel.readSample(0)
        viewModel.setReference(0, "50")
        viewModel.setReference(1, "200")

        viewModel.propose()
        assertEquals(CalibrationInputError.NEED_SAMPLE, viewModel.uiState.value.inputError)

        sensor.nextReading = SensorSample.Success(testReading.copy(potassiumMgPerKg = 140), 0)
        viewModel.readSample(1)
        viewModel.propose()

        val proposal = assertIs<CalibrationProposal.Change>(viewModel.uiState.value.proposal)
        assertEquals(CalibrationWrite.Npk(Nutrient.POTASSIUM, NpkCalibration(1.5f, -10)), proposal.write)
    }

    @Test
    fun `factor input is validated against the register range`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        connected()
        viewModel.selectTarget(CalibrationTarget.TDS_FACTOR)

        viewModel.setFactorInput("1.4")
        viewModel.propose()
        assertEquals(CalibrationInputError.OUT_OF_RANGE, viewModel.uiState.value.inputError)

        viewModel.setFactorInput("abc")
        viewModel.propose()
        assertEquals(CalibrationInputError.INVALID_NUMBER, viewModel.uiState.value.inputError)

        viewModel.setFactorInput("0,64")
        viewModel.propose()
        val proposal = assertIs<CalibrationProposal.Change>(viewModel.uiState.value.proposal)
        assertEquals(CalibrationWrite.Factor(FactorParameter.TDS, 64), proposal.write)
    }

    @Test
    fun `offset without a sample asks for one`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        connected()
        viewModel.setReference(0, "25")

        viewModel.propose()

        assertEquals(CalibrationInputError.NEED_SAMPLE, viewModel.uiState.value.inputError)
    }

    @Test
    fun `write failure is reported and the proposal closed`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val events = mutableListOf<CalibrationEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        connected()
        viewModel.requestRestoreDefaults()
        sensor.failure = SensorReadError.NoResponse

        viewModel.confirmProposal()

        assertNull(viewModel.uiState.value.proposal)
        assertEquals(listOf<CalibrationEvent>(CalibrationEvent.Failed(SensorReadError.NoResponse)), events)
    }

    @Test
    fun `calibration is forgotten when the probe disconnects`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        connected()

        sensor.disconnect()

        assertNull(viewModel.uiState.value.calibration)
    }
}
