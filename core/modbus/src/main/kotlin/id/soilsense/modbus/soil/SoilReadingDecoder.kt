package id.soilsense.modbus.soil

/**
 * Converts raw holding registers (starting at 0x0000) into engineering units.
 *
 * Every data register is a signed 16-bit value, as in the vendor's Config Tool V1.3
 * (`cwt.sensors.js`: valueType Signed for registers 1–9). Only temperature is negative in normal use,
 * but calibration offsets can push any value below zero.
 */
public object SoilReadingDecoder {

    /** Accepts 7 registers (no salinity/TDS) or 9 and more. */
    public fun decode(registers: IntArray): SoilReading {
        require(registers.size >= SoilSensorRegisters.MEASUREMENT_COUNT) {
            "Need at least ${SoilSensorRegisters.MEASUREMENT_COUNT} registers, got ${registers.size}"
        }
        val extended = registers.size >= SoilSensorRegisters.EXTENDED_COUNT
        return SoilReading(
            moisturePercent = registers[SoilSensorRegisters.MOISTURE].toSigned16() / 10.0,
            temperatureCelsius = registers[SoilSensorRegisters.TEMPERATURE].toSigned16() / 10.0,
            conductivityMicroSiemensPerCm = registers[SoilSensorRegisters.CONDUCTIVITY].toSigned16(),
            ph = registers[SoilSensorRegisters.PH].toSigned16() / 10.0,
            nitrogenMgPerKg = registers[SoilSensorRegisters.NITROGEN].toSigned16(),
            phosphorusMgPerKg = registers[SoilSensorRegisters.PHOSPHORUS].toSigned16(),
            potassiumMgPerKg = registers[SoilSensorRegisters.POTASSIUM].toSigned16(),
            salinityMgPerL = if (extended) registers[SoilSensorRegisters.SALINITY].toSigned16() else null,
            tdsMgPerL = if (extended) registers[SoilSensorRegisters.TDS].toSigned16() else null,
        )
    }

    private fun Int.toSigned16(): Int = if (this >= 0x8000) this - 0x10000 else this
}
