package id.soilsense.modbus

/** Builders for the Modbus RTU request frames this app needs. */
public object ModbusFrames {

    public const val FC_READ_HOLDING_REGISTERS: Int = 0x03
    public const val FC_WRITE_SINGLE_REGISTER: Int = 0x06
    public const val FC_WRITE_MULTIPLE_REGISTERS: Int = 0x10

    /** Modbus limit for one function-0x10 request. */
    public const val MAX_WRITE_QUANTITY: Int = 123

    /** Valid unicast addresses plus 0xFF, which the CWT sensor answers regardless of its configured ID. */
    public val SLAVE_ID_RANGE: IntRange = 1..255

    /** Modbus limit for one function-0x03 request. */
    public const val MAX_READ_QUANTITY: Int = 125

    public fun readHoldingRegisters(slaveId: Int, startAddress: Int, quantity: Int): ByteArray {
        require(slaveId in SLAVE_ID_RANGE) { "Slave ID $slaveId out of range" }
        require(startAddress in 0..0xFFFF) { "Register address $startAddress out of range" }
        require(quantity in 1..MAX_READ_QUANTITY) { "Quantity $quantity out of range" }
        return Crc16.appendCrc(
            byteArrayOf(
                slaveId.toByte(),
                FC_READ_HOLDING_REGISTERS.toByte(),
                (startAddress ushr 8).toByte(),
                startAddress.toByte(),
                (quantity ushr 8).toByte(),
                quantity.toByte(),
            ),
        )
    }

    public fun writeSingleRegister(slaveId: Int, address: Int, value: Int): ByteArray {
        require(slaveId in SLAVE_ID_RANGE) { "Slave ID $slaveId out of range" }
        require(address in 0..0xFFFF) { "Register address $address out of range" }
        require(value in 0..0xFFFF) { "Register value $value out of range" }
        return Crc16.appendCrc(
            byteArrayOf(
                slaveId.toByte(),
                FC_WRITE_SINGLE_REGISTER.toByte(),
                (address ushr 8).toByte(),
                address.toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            ),
        )
    }

    /** Function 0x10: [values] are unsigned 16-bit words written from [startAddress] upwards. */
    public fun writeMultipleRegisters(slaveId: Int, startAddress: Int, values: IntArray): ByteArray {
        require(slaveId in SLAVE_ID_RANGE) { "Slave ID $slaveId out of range" }
        require(startAddress in 0..0xFFFF) { "Register address $startAddress out of range" }
        require(values.size in 1..MAX_WRITE_QUANTITY) { "Quantity ${values.size} out of range" }
        require(values.all { it in 0..0xFFFF }) { "Register values must be 0..65535" }
        val header = byteArrayOf(
            slaveId.toByte(),
            FC_WRITE_MULTIPLE_REGISTERS.toByte(),
            (startAddress ushr 8).toByte(),
            startAddress.toByte(),
            (values.size ushr 8).toByte(),
            values.size.toByte(),
            (values.size * 2).toByte(),
        )
        val data = ByteArray(values.size * 2) { i ->
            val word = values[i / 2]
            (if (i % 2 == 0) word ushr 8 else word).toByte()
        }
        return Crc16.appendCrc(header + data)
    }
}
