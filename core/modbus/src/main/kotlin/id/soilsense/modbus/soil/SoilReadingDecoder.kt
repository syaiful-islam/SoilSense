package id.soilsense.modbus.soil

/** Converts raw holding registers (starting at 0x0000) into engineering units. */
public object SoilReadingDecoder {

    public fun decode(registers: IntArray): SoilReading {
        require(registers.size >= SoilSensorRegisters.BASIC_COUNT) {
            "Need at least ${SoilSensorRegisters.BASIC_COUNT} registers, got ${registers.size}"
        }
        val extended = registers.size >= SoilSensorRegisters.EXTENDED_COUNT
        return SoilReading(
            moisturePercent = registers[SoilSensorRegisters.MOISTURE] / 10.0,
            // The probe is rated down to -40 °C, so temperature is two's complement.
            temperatureCelsius = registers[SoilSensorRegisters.TEMPERATURE].toSigned16() / 10.0,
            conductivityMicroSiemensPerCm = registers[SoilSensorRegisters.CONDUCTIVITY],
            ph = registers[SoilSensorRegisters.PH] / 10.0,
            nitrogenMgPerKg = registers[SoilSensorRegisters.NITROGEN],
            phosphorusMgPerKg = registers[SoilSensorRegisters.PHOSPHORUS],
            potassiumMgPerKg = registers[SoilSensorRegisters.POTASSIUM],
            salinityMgPerL = if (extended) registers[SoilSensorRegisters.SALINITY] else null,
            tdsMgPerL = if (extended) registers[SoilSensorRegisters.TDS] else null,
        )
    }

    private fun Int.toSigned16(): Int = if (this >= 0x8000) this - 0x10000 else this
}
