package id.soilsense.modbus.soil

/**
 * Communication settings stored inside the probe (registers 0x07D0 / 0x07D1).
 *
 * @property baudRate null when the probe reports a baud code the manual does not list.
 */
public data class ProbeConfig(val slaveId: Int, val baudRate: Int?)

/** The probe stores its baud rate as a code: 0 = 2400, 1 = 4800, 2 = 9600 (manual V1.4, page 4). */
public object ProbeBaudRate {
    /** The register code for [baudRate], or null if the probe cannot use it. */
    public fun code(baudRate: Int): Int? =
        SoilSensorRegisters.SUPPORTED_BAUD_RATES.indexOf(baudRate).takeIf { it >= 0 }

    public fun fromCode(code: Int): Int? = SoilSensorRegisters.SUPPORTED_BAUD_RATES.getOrNull(code)
}
