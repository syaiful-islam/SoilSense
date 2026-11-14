package id.soilsense.modbus.soil

/**
 * Calibration registers of the CWT-SOIL-NPKPHCTH-S probe (manual V1.4 and the vendor's Config Tool
 * V1.3). The probe applies `reading = factor × raw + offset` internally and keeps these values in
 * its own memory, so a write survives power cycles.
 */

/** Single-point corrections: the probe adds the offset to every reading. Values are signed int16. */
public enum class OffsetParameter(
    public val register: Int,
    /** Engineering units per raw step. */
    public val scale: Double,
) {
    TEMPERATURE(0x0050, 0.1), // °C
    MOISTURE(0x0051, 0.1), // %
    CONDUCTIVITY(0x0052, 1.0), // µS/cm

    /**
     * The manual gives the step only as "1". The pH register itself is in 0.1 pH, so 0.1 pH per
     * step is assumed; confirm on the device before relying on pH calibration.
     */
    PH(0x0053, 0.1),
}

/** Factors stored as 0–100. */
public enum class FactorParameter(
    public val register: Int,
    public val scale: Double,
    public val defaultRaw: Int,
) {
    /** EC temperature compensation, 0–100 = 0.0–10.0 %/°C. */
    EC_TEMPERATURE_COEFFICIENT(0x0022, 0.1, 0),

    /** Salinity = EC × factor, 0–100 = 0.00–1.00. */
    SALINITY(0x0023, 0.01, 55),

    /** TDS = EC × factor, 0–100 = 0.00–1.00. */
    TDS(0x0024, 0.01, 50),
    ;

    public companion object {
        public val RAW_RANGE: IntRange = 0..100
    }
}

/** N, P and K: `reading = factor × raw + offset`, factor is a big-endian IEEE-754 float in two registers. */
public enum class Nutrient(
    public val dataRegister: Int,
    public val factorRegister: Int,
    public val offsetRegister: Int,
) {
    NITROGEN(0x0004, 0x04E8, 0x04EA),
    PHOSPHORUS(0x0005, 0x04F2, 0x04F4),
    POTASSIUM(0x0006, 0x04FC, 0x04FE),
    ;

    public fun valueIn(reading: SoilReading): Int = when (this) {
        NITROGEN -> reading.nitrogenMgPerKg
        PHOSPHORUS -> reading.phosphorusMgPerKg
        POTASSIUM -> reading.potassiumMgPerKg
    }
}

public data class NpkCalibration(val factor: Float, val offset: Int) {
    public companion object {
        public val DEFAULT: NpkCalibration = NpkCalibration(factor = 1.0f, offset = 0)
    }
}

/** Raw register values as stored on the probe. */
public data class CalibrationSettings(
    val offsets: Map<OffsetParameter, Int>,
    val factors: Map<FactorParameter, Int>,
    val npk: Map<Nutrient, NpkCalibration>,
)

/** One change to the probe's calibration memory. */
public sealed interface CalibrationWrite {
    public data class Offset(val parameter: OffsetParameter, val raw: Int) : CalibrationWrite {
        init {
            require(raw in -32_768..32_767) { "Offset $raw does not fit int16" }
        }
    }

    public data class Factor(val parameter: FactorParameter, val raw: Int) : CalibrationWrite {
        init {
            require(raw in FactorParameter.RAW_RANGE) { "Factor $raw outside 0..100" }
        }
    }

    public data class Npk(val nutrient: Nutrient, val calibration: NpkCalibration) : CalibrationWrite {
        init {
            require(calibration.factor.isFinite() && calibration.factor > 0f) { "Factor must be positive" }
            require(calibration.offset in -32_768..32_767) { "Offset does not fit int16" }
        }
    }

    /**
     * The vendor's "Reset N P K": write 0xFFFF to the N/P/K data registers (a value written there
     * freezes the reading; 0xFFFF resumes measuring) and restore factor 1.0 / offset 0.
     */
    public data object ResetNpk : CalibrationWrite

    /** All offsets 0, factors to their factory defaults, plus [ResetNpk]. */
    public data object RestoreDefaults : CalibrationWrite
}

/** Pure arithmetic for turning a reference measurement into new calibration values. */
public object CalibrationMath {

    private val INT16 = -32_768L..32_767L

    /**
     * New raw offset so that a probe currently showing [reading] (with [currentRaw] already applied)
     * shows [reference] instead. Null if the result does not fit the register.
     */
    public fun offset(parameter: OffsetParameter, currentRaw: Int, reading: Double, reference: Double): Int? {
        val delta = Math.round((reference - reading) / parameter.scale)
        val raw = currentRaw + delta
        return raw.toInt().takeIf { raw in INT16 }
    }

    /** Raw factor for an engineering value, or null outside 0..100 raw. */
    public fun factorRaw(parameter: FactorParameter, value: Double): Int? {
        if (!value.isFinite()) return null
        val raw = Math.round(value / parameter.scale)
        return raw.toInt().takeIf { raw in FactorParameter.RAW_RANGE.first.toLong()..FactorParameter.RAW_RANGE.last.toLong() }
    }

    /** One reference sample: keep the factor, shift the offset by the error. */
    public fun npkOnePoint(current: NpkCalibration, reading: Int, reference: Int): NpkCalibration? {
        val offset = current.offset.toLong() + (reference - reading)
        return if (offset in INT16) current.copy(offset = offset.toInt()) else null
    }

    /**
     * Two reference samples measured with [current] applied. The probe's raw values are recovered
     * as `(reading − offset) / factor`, then a new line through both references is fitted.
     * Null when the samples are too close to tell apart or the result is not usable.
     */
    public fun npkTwoPoint(
        current: NpkCalibration,
        reading1: Int,
        reference1: Int,
        reading2: Int,
        reference2: Int,
    ): NpkCalibration? {
        if (current.factor == 0f) return null
        val raw1 = (reading1 - current.offset) / current.factor.toDouble()
        val raw2 = (reading2 - current.offset) / current.factor.toDouble()
        if (kotlin.math.abs(raw2 - raw1) < MIN_RAW_SPAN) return null
        val factor = (reference2 - reference1) / (raw2 - raw1)
        if (!factor.isFinite() || factor <= 0.0) return null
        val offset = Math.round(reference1 - factor * raw1)
        if (offset !in INT16) return null
        return NpkCalibration(factor.toFloat(), offset.toInt())
    }

    /** Samples whose raw values differ by less than this cannot define a slope reliably. */
    public const val MIN_RAW_SPAN: Double = 5.0
}
