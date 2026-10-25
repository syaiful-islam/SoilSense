package id.soilsense.modbus.soil

/**
 * Register map from the "CWT Soil sensor (NPK type) manual" V1.4.
 *
 * The manual's tables list the function codes as 0x30 / 0x60, but every example frame in the same
 * manual uses the standard Modbus codes 0x03 / 0x06, which is what the probe actually implements.
 */
public object SoilSensorRegisters {
    public const val MOISTURE: Int = 0x0000 // 0.1 %RH
    public const val TEMPERATURE: Int = 0x0001 // 0.1 °C, signed
    public const val CONDUCTIVITY: Int = 0x0002 // 1 µS/cm
    public const val PH: Int = 0x0003 // 0.1 pH
    public const val NITROGEN: Int = 0x0004 // 1 mg/kg
    public const val PHOSPHORUS: Int = 0x0005 // 1 mg/kg
    public const val POTASSIUM: Int = 0x0006 // 1 mg/kg
    public const val SALINITY: Int = 0x0007 // 1 mg/L
    public const val TDS: Int = 0x0008 // 1 mg/L

    public const val SLAVE_ID: Int = 0x07D0
    public const val BAUD_RATE: Int = 0x07D1

    /** Moisture … potassium. */
    public const val BASIC_COUNT: Int = 7

    /** Moisture … TDS. */
    public const val EXTENDED_COUNT: Int = 9

    /** The probe answers this address regardless of its configured slave ID. */
    public const val BROADCAST_QUERY_ADDRESS: Int = 0xFF

    public const val DEFAULT_SLAVE_ID: Int = 1
    public const val DEFAULT_BAUD_RATE: Int = 4800
    public val SUPPORTED_BAUD_RATES: List<Int> = listOf(2400, 4800, 9600)
}
