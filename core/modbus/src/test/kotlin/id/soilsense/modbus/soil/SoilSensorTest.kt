package id.soilsense.modbus.soil

import id.soilsense.modbus.Crc16
import id.soilsense.modbus.FakeSerialTransport
import id.soilsense.modbus.ModbusException
import id.soilsense.modbus.ModbusRtuClient
import id.soilsense.modbus.hex
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.TestTimeSource

class SoilSensorTest {

    private val timeSource = TestTimeSource()
    private val transport = FakeSerialTransport(timeSource)
    private val sensor = SoilSensor(ModbusRtuClient(transport, timeSource = timeSource))

    private val sevenRegisterReply = hex("01 03 0E 01 D0 01 4C 00 2C 00 5A 00 20 00 58 00 68 70 29")
    private val nineRegisterReply =
        Crc16.appendCrc(hex("01 03 12 01 D0 01 4C 00 2C 00 5A 00 20 00 58 00 68 00 18 00 16"))
    private val illegalAddressReply = Crc16.appendCrc(hex("01 83 02"))

    private val readNine = hex("01 03 00 00 00 09 85 CC")
    private val readSeven = hex("01 03 00 00 00 07 04 08")

    @Test
    fun `reads all nine registers when supported`() = runTest {
        transport.enqueueReply(nineRegisterReply)

        val reading = sensor.read(slaveId = 1)

        assertEquals(24, reading.salinityMgPerL)
        assertEquals(22, reading.tdsMgPerL)
        assertContentEquals(readNine, transport.writtenFrames.single())
    }

    @Test
    fun `falls back to seven registers on illegal data address and remembers it`() = runTest {
        transport.enqueueReply(illegalAddressReply)
        transport.enqueueReply(sevenRegisterReply)
        transport.enqueueReply(sevenRegisterReply)

        val first = sensor.read(slaveId = 1)
        val second = sensor.read(slaveId = 1)

        assertNull(first.salinityMgPerL)
        assertEquals(46.4, second.moisturePercent, 1e-9)
        assertEquals(listOf(readNine, readSeven, readSeven).map { it.toList() }, transport.writtenFrames.map { it.toList() })
    }

    @Test
    fun `falls back to seven registers when the probe ignores the nine-register read`() = runTest {
        transport.enqueueReply(null)
        transport.enqueueReply(sevenRegisterReply)

        val reading = sensor.read(slaveId = 1)

        assertEquals(104, reading.potassiumMgPerKg)
    }

    @Test
    fun `timeout after extended support is known is reported, not masked`() = runTest {
        transport.enqueueReply(nineRegisterReply)
        transport.enqueueReply(null)

        sensor.read(slaveId = 1)

        assertFailsWith<ModbusException.ResponseTimeout> { sensor.read(slaveId = 1) }
        assertEquals(2, transport.writtenFrames.size)
    }

    @Test
    fun `disconnected probe reports timeout`() = runTest {
        transport.enqueueReply(null)
        transport.enqueueReply(null)

        assertFailsWith<ModbusException.ResponseTimeout> { sensor.read(slaveId = 1) }
    }

    @Test
    fun `queryAddress uses the 0xFF enquiry from the manual`() = runTest {
        transport.enqueueReply(hex("FF 03 02 00 01 50 50"))

        assertEquals(1, sensor.queryAddress())
        assertContentEquals(hex("FF 03 07 D0 00 01 91 59"), transport.writtenFrames.single())
    }
}
