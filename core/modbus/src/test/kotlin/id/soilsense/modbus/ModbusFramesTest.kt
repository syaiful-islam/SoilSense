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
    fun `write multiple matches the vendor frame for N factor 1_0`() {
        // From the vendor Config Tool's "Reset N P K": N factor (0x04E8) = 1.0f = 3F80 0000.
        assertContentEquals(
            hex("01 10 04 E8 00 02 04 3F 80 00 00 C3 7D"),
            ModbusFrames.writeMultipleRegisters(slaveId = 1, startAddress = 0x04E8, values = intArrayOf(0x3F80, 0x0000)),
        )
    }

    @Test
    fun `write single matches the vendor frame for resuming N measurement`() {
        assertContentEquals(
            hex("01 06 00 04 FF FF C9 BB"),
            ModbusFrames.writeSingleRegister(slaveId = 1, address = 0x0004, value = 0xFFFF),
        )
    }

    @Test
    fun `invalid arguments are rejected`() {
        assertFailsWith<IllegalArgumentException> { ModbusFrames.readHoldingRegisters(0, 0, 1) }
        assertFailsWith<IllegalArgumentException> { ModbusFrames.readHoldingRegisters(1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { ModbusFrames.readHoldingRegisters(1, 0x10000, 1) }
        assertFailsWith<IllegalArgumentException> { ModbusFrames.writeSingleRegister(1, 0, 0x10000) }
        assertFailsWith<IllegalArgumentException> { ModbusFrames.writeMultipleRegisters(1, 0, IntArray(0)) }
        assertFailsWith<IllegalArgumentException> { ModbusFrames.writeMultipleRegisters(1, 0, intArrayOf(-1)) }
    }
}
