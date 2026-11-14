package id.soilsense.modbus.soil

import id.soilsense.modbus.ModbusRtuClient
import id.soilsense.modbus.SimulatedProbe
import id.soilsense.modbus.hex
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProbeConfigTest {

    private fun probe() = SimulatedProbe(registers = mutableMapOf(0x07D0 to 1, 0x07D1 to 1))

    @Test
    fun `reads slave ID and baud rate stored in the probe`() = runTest {
        val config = SoilSensor(ModbusRtuClient(probe())).readProbeConfig(slaveId = 1)

        assertEquals(ProbeConfig(slaveId = 1, baudRate = 4800), config)
    }

    @Test
    fun `set slave ID frame matches the manual`() = runTest {
        val probe = probe()

        SoilSensor(ModbusRtuClient(probe)).writeSlaveId(slaveId = 1, newSlaveId = 2)

        assertContentEquals(hex("01 06 07 D0 00 02 08 86"), probe.requests.single())
        assertEquals(2, probe.registers[0x07D0])
    }

    @Test
    fun `set baud rate frame matches the manual`() = runTest {
        val probe = probe()

        SoilSensor(ModbusRtuClient(probe)).writeBaudRate(slaveId = 1, baudRate = 9600)

        assertContentEquals(hex("01 06 07 D1 00 02 59 46"), probe.requests.single())
        assertEquals(2, probe.registers[0x07D1])
    }

    @Test
    fun `invalid values are refused before anything is sent`() = runTest {
        val probe = probe()
        val sensor = SoilSensor(ModbusRtuClient(probe))

        assertFailsWith<IllegalArgumentException> { sensor.writeSlaveId(1, 0) }
        assertFailsWith<IllegalArgumentException> { sensor.writeSlaveId(1, 255) }
        assertFailsWith<IllegalArgumentException> { sensor.writeBaudRate(1, 19200) }
        assertEquals(0, probe.requests.size)
    }

    @Test
    fun `baud codes map both ways`() {
        assertEquals(0, ProbeBaudRate.code(2400))
        assertEquals(2, ProbeBaudRate.code(9600))
        assertNull(ProbeBaudRate.code(115200))
        assertEquals(4800, ProbeBaudRate.fromCode(1))
        assertNull(ProbeBaudRate.fromCode(7))
    }
}
