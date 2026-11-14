package id.soilsense.app.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.soilsense.app.data.model.ConnectionState
import id.soilsense.app.data.model.SensorReadError
import id.soilsense.app.data.model.SensorSample
import id.soilsense.app.data.sensor.SensorResult
import id.soilsense.app.data.sensor.SoilSensorRepository
import id.soilsense.modbus.soil.CalibrationMath
import id.soilsense.modbus.soil.CalibrationSettings
import id.soilsense.modbus.soil.CalibrationWrite
import id.soilsense.modbus.soil.FactorParameter
import id.soilsense.modbus.soil.NpkCalibration
import id.soilsense.modbus.soil.Nutrient
import id.soilsense.modbus.soil.OffsetParameter
import id.soilsense.modbus.soil.SoilReading
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** What the user is calibrating; each maps to one kind of probe register. */
enum class CalibrationTarget(
    val offsetParameter: OffsetParameter? = null,
    val nutrient: Nutrient? = null,
    val factorParameter: FactorParameter? = null,
) {
    TEMPERATURE(offsetParameter = OffsetParameter.TEMPERATURE),
    MOISTURE(offsetParameter = OffsetParameter.MOISTURE),
    CONDUCTIVITY(offsetParameter = OffsetParameter.CONDUCTIVITY),
    PH(offsetParameter = OffsetParameter.PH),
    NITROGEN(nutrient = Nutrient.NITROGEN),
    PHOSPHORUS(nutrient = Nutrient.PHOSPHORUS),
    POTASSIUM(nutrient = Nutrient.POTASSIUM),
    EC_TEMPERATURE_COEFFICIENT(factorParameter = FactorParameter.EC_TEMPERATURE_COEFFICIENT),
    SALINITY_FACTOR(factorParameter = FactorParameter.SALINITY),
    TDS_FACTOR(factorParameter = FactorParameter.TDS),
    ;

    /** Offsets and N/P/K are calibrated against a reference measurement; factors are typed in. */
    val needsSample: Boolean get() = factorParameter == null

    /** The value this target corrects, as the probe reports it now. */
    fun currentValue(reading: SoilReading): Double? = when (this) {
        TEMPERATURE -> reading.temperatureCelsius
        MOISTURE -> reading.moisturePercent
        CONDUCTIVITY -> reading.conductivityMicroSiemensPerCm.toDouble()
        PH -> reading.ph
        NITROGEN, PHOSPHORUS, POTASSIUM -> nutrient?.valueIn(reading)?.toDouble()
        EC_TEMPERATURE_COEFFICIENT, SALINITY_FACTOR, TDS_FACTOR -> null
    }
}

enum class CalibrationInputError { NOT_LOADED, NEED_SAMPLE, INVALID_NUMBER, OUT_OF_RANGE, INVALID_POINTS }

/** A value as stored in the probe, kept raw so the screen can format it with units. */
sealed interface CalibrationValue {
    data class Offset(val parameter: OffsetParameter, val raw: Int) : CalibrationValue

    data class Factor(val parameter: FactorParameter, val raw: Int) : CalibrationValue

    data class Npk(val calibration: NpkCalibration) : CalibrationValue
}

/** A pending write, shown in a confirmation dialog before anything reaches the probe. */
sealed interface CalibrationProposal {
    val write: CalibrationWrite

    data class Change(
        val target: CalibrationTarget,
        val before: CalibrationValue,
        val after: CalibrationValue,
        override val write: CalibrationWrite,
    ) : CalibrationProposal

    data object ResetNpk : CalibrationProposal {
        override val write: CalibrationWrite = CalibrationWrite.ResetNpk
    }

    data object RestoreDefaults : CalibrationProposal {
        override val write: CalibrationWrite = CalibrationWrite.RestoreDefaults
    }
}

data class CalibrationUiState(
    val isConnected: Boolean = false,
    val calibration: CalibrationSettings? = null,
    val isLoading: Boolean = false,
    val target: CalibrationTarget = CalibrationTarget.TEMPERATURE,
    /** N/P/K only: fit factor and offset from two lab samples instead of shifting the offset. */
    val twoPoint: Boolean = false,
    val samples: List<SoilReading?> = listOf(null, null),
    val references: List<String> = listOf("", ""),
    val factorInput: String = "",
    val inputError: CalibrationInputError? = null,
    val proposal: CalibrationProposal? = null,
    val isSampling: Boolean = false,
    val isWriting: Boolean = false,
)

sealed interface CalibrationEvent {
    data object Saved : CalibrationEvent

    data class Failed(val error: SensorReadError) : CalibrationEvent
}

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val sensorRepository: SoilSensorRepository,
) : ViewModel() {

    private val form = MutableStateFlow(CalibrationUiState())
    private val _events = Channel<CalibrationEvent>(Channel.BUFFERED)
    val events: Flow<CalibrationEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<CalibrationUiState> =
        combine(sensorRepository.connectionState, form) { connection, state ->
            state.copy(isConnected = connection is ConnectionState.Connected)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalibrationUiState())

    init {
        // Load the probe's calibration whenever a probe gets connected; forget it when it goes away.
        viewModelScope.launch {
            sensorRepository.connectionState.collect { connection ->
                if (connection is ConnectionState.Connected) {
                    if (form.value.calibration == null && !form.value.isLoading) refresh()
                } else {
                    form.update { it.copy(calibration = null, samples = listOf(null, null), proposal = null) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            form.update { it.copy(isLoading = true) }
            when (val result = sensorRepository.readCalibration()) {
                is SensorResult.Success -> form.update { it.copy(calibration = result.value, isLoading = false) }
                is SensorResult.Failure -> {
                    form.update { it.copy(isLoading = false) }
                    _events.send(CalibrationEvent.Failed(result.error))
                }
            }
        }
    }

    fun selectTarget(target: CalibrationTarget) {
        form.update {
            it.copy(
                target = target,
                twoPoint = it.twoPoint && target.nutrient != null,
                samples = listOf(null, null),
                references = listOf("", ""),
                factorInput = "",
                inputError = null,
            )
        }
    }

    fun setTwoPoint(enabled: Boolean) {
        form.update { it.copy(twoPoint = enabled, samples = listOf(null, null), references = listOf("", ""), inputError = null) }
    }

    /** Takes a fresh reading from the probe as calibration sample [index] (0 or 1). */
    fun readSample(index: Int) {
        require(index in 0..1) { "Sample index $index" }
        viewModelScope.launch {
            form.update { it.copy(isSampling = true) }
            when (val sample = sensorRepository.readNow()) {
                is SensorSample.Success -> form.update {
                    it.copy(samples = it.samples.toMutableList().apply { set(index, sample.reading) }, isSampling = false, inputError = null)
                }
                is SensorSample.Failure -> {
                    form.update { it.copy(isSampling = false) }
                    _events.send(CalibrationEvent.Failed(sample.error))
                }
            }
        }
    }

    fun setReference(index: Int, text: String) {
        form.update { it.copy(references = it.references.toMutableList().apply { set(index, text) }, inputError = null) }
    }

    fun setFactorInput(text: String) {
        form.update { it.copy(factorInput = text, inputError = null) }
    }

    /** Validates the inputs and, if they make sense, opens the confirmation for the resulting write. */
    fun propose() {
        val state = form.value
        val calibration = state.calibration
        val result = if (calibration == null) {
            Proposed.Invalid(CalibrationInputError.NOT_LOADED)
        } else {
            buildProposal(state, calibration)
        }
        form.update {
            when (result) {
                is Proposed.Valid -> it.copy(proposal = result.proposal, inputError = null)
                is Proposed.Invalid -> it.copy(inputError = result.error)
            }
        }
    }

    fun requestResetNpk() = form.update { it.copy(proposal = CalibrationProposal.ResetNpk) }

    fun requestRestoreDefaults() = form.update { it.copy(proposal = CalibrationProposal.RestoreDefaults) }

    fun dismissProposal() = form.update { it.copy(proposal = null) }

    fun confirmProposal() {
        val proposal = form.value.proposal ?: return
        if (form.value.isWriting) return
        viewModelScope.launch {
            form.update { it.copy(isWriting = true) }
            when (val result = sensorRepository.applyCalibration(proposal.write)) {
                is SensorResult.Success -> {
                    form.update {
                        it.copy(
                            calibration = result.value,
                            proposal = null,
                            isWriting = false,
                            samples = listOf(null, null),
                            references = listOf("", ""),
                            factorInput = "",
                        )
                    }
                    _events.send(CalibrationEvent.Saved)
                }
                is SensorResult.Failure -> {
                    form.update { it.copy(proposal = null, isWriting = false) }
                    _events.send(CalibrationEvent.Failed(result.error))
                }
            }
        }
    }

    private sealed interface Proposed {
        data class Valid(val proposal: CalibrationProposal) : Proposed

        data class Invalid(val error: CalibrationInputError) : Proposed
    }

    private fun buildProposal(state: CalibrationUiState, calibration: CalibrationSettings): Proposed {
        val target = state.target
        target.factorParameter?.let { parameter ->
            val value = parseNumber(state.factorInput) ?: return Proposed.Invalid(CalibrationInputError.INVALID_NUMBER)
            val raw = CalibrationMath.factorRaw(parameter, value) ?: return Proposed.Invalid(CalibrationInputError.OUT_OF_RANGE)
            val before = calibration.factors.getValue(parameter)
            return Proposed.Valid(
                CalibrationProposal.Change(
                    target,
                    CalibrationValue.Factor(parameter, before),
                    CalibrationValue.Factor(parameter, raw),
                    CalibrationWrite.Factor(parameter, raw),
                ),
            )
        }

        val sample1 = state.samples[0] ?: return Proposed.Invalid(CalibrationInputError.NEED_SAMPLE)
        val reference1 = parseNumber(state.references[0]) ?: return Proposed.Invalid(CalibrationInputError.INVALID_NUMBER)

        target.offsetParameter?.let { parameter ->
            val reading = target.currentValue(sample1) ?: return Proposed.Invalid(CalibrationInputError.NEED_SAMPLE)
            val before = calibration.offsets.getValue(parameter)
            val raw = CalibrationMath.offset(parameter, before, reading, reference1)
                ?: return Proposed.Invalid(CalibrationInputError.OUT_OF_RANGE)
            return Proposed.Valid(
                CalibrationProposal.Change(
                    target,
                    CalibrationValue.Offset(parameter, before),
                    CalibrationValue.Offset(parameter, raw),
                    CalibrationWrite.Offset(parameter, raw),
                ),
            )
        }

        val nutrient = requireNotNull(target.nutrient)
        if (reference1 < 0) return Proposed.Invalid(CalibrationInputError.OUT_OF_RANGE)
        val before = calibration.npk.getValue(nutrient)
        val after = if (state.twoPoint) {
            val sample2 = state.samples[1] ?: return Proposed.Invalid(CalibrationInputError.NEED_SAMPLE)
            val reference2 = parseNumber(state.references[1]) ?: return Proposed.Invalid(CalibrationInputError.INVALID_NUMBER)
            if (reference2 < 0) return Proposed.Invalid(CalibrationInputError.OUT_OF_RANGE)
            CalibrationMath.npkTwoPoint(
                before,
                nutrient.valueIn(sample1),
                reference1.roundToInt(),
                nutrient.valueIn(sample2),
                reference2.roundToInt(),
            ) ?: return Proposed.Invalid(CalibrationInputError.INVALID_POINTS)
        } else {
            CalibrationMath.npkOnePoint(before, nutrient.valueIn(sample1), reference1.roundToInt())
                ?: return Proposed.Invalid(CalibrationInputError.OUT_OF_RANGE)
        }
        return Proposed.Valid(
            CalibrationProposal.Change(
                target,
                CalibrationValue.Npk(before),
                CalibrationValue.Npk(after),
                CalibrationWrite.Npk(nutrient, after),
            ),
        )
    }

    /** Accepts "7,5" as well as "7.5": Indonesian keyboards default to a decimal comma. */
    private fun parseNumber(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
