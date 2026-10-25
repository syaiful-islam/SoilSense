package id.soilsense.modbus

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ModbusFramesTest {

    @Test
    fun `read request matches manual example`() {
        assertContentEquals(
            hex("01 03 00 00 00 07 04 08"),
            ModbusFrames.readHoldingRegisters(slaveId = 1, startAddress = 0, quantity = 7),
        )
    }

    @Test
    fun `slave id enquiry matches manual example`() {
        assertContentEquals(
            hex("FF 03 07 D0 00 01 91 59"),
            ModbusFrames.readHoldingRegisters(slaveId = 0xFF, startAddress = 0x07D0, quantity = 1),
        )
    }

    @Test
    fun `write request matches manual example`() {
        assertContentEquals(
            hex("01 06 07 D1 00 02 59 46"),
            ModbusFrames.writeSingleRegister(slaveId = 1, address = 0x07D1, value = 2),
        )
    }

    @Test
    fun `invalid arguments are rejected`() {
        assertFailsWith<IllegalArgumentException> { ModbusFrames.readHoldingRegisters(0, 0, 1) }
        assertFailsWith<IllegalArgumentException> { ModbusFrames.readHoldingRegisters(1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { ModbusFrames.readHoldingRegisters(1, 0x10000, 1) }
        assertFailsWith<IllegalArgumentException> { ModbusFrames.writeSingleRegister(1, 0, 0x10000) }
    }
}
