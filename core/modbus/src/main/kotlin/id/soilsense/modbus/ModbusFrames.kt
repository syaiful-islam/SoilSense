package id.soilsense.modbus

/** Builders for the Modbus RTU request frames this app needs. */
public object ModbusFrames {

    public const val FC_READ_HOLDING_REGISTERS: Int = 0x03
    public const val FC_WRITE_SINGLE_REGISTER: Int = 0x06

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
}
