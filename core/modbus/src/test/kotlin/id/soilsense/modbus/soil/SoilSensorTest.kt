package id.soilsense.modbus.soil

import id.soilsense.modbus.FakeSerialTransport
import id.soilsense.modbus.ModbusException
import id.soilsense.modbus.ModbusRtuClient
import id.soilsense.modbus.hex
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.TestTimeSource

class SoilSensorTest {

    private val timeSource = TestTimeSource()
    private val transport = FakeSerialTransport(timeSource)
    private val sensor = SoilSensor(ModbusRtuClient(transport, timeSource = timeSource))

    @Test
    fun `reads the seven parameters with the manual's request frame`() = runTest {
        transport.enqueueReply(hex("01 03 0E 01 D0 01 4C 00 2C 00 5A 00 20 00 58 00 68 70 29"))

        val reading = sensor.read(slaveId = 1)

        assertContentEquals(hex("01 03 00 00 00 07 04 08"), transport.writtenFrames.single())
        assertEquals(32, reading.nitrogenMgPerKg)
        assertEquals(88, reading.phosphorusMgPerKg)
        assertEquals(104, reading.potassiumMgPerKg)
    }

    @Test
    fun `silent probe reports timeout after a single request`() = runTest {
        transport.enqueueReply(null)

        assertFailsWith<ModbusException.ResponseTimeout> { sensor.read(slaveId = 1) }
        assertEquals(1, transport.writtenFrames.size)
    }

    @Test
    fun `queryAddress uses the 0xFF enquiry from the manual`() = runTest {
        transport.enqueueReply(hex("FF 03 02 00 01 50 50"))

        assertEquals(1, sensor.queryAddress())
        assertContentEquals(hex("FF 03 07 D0 00 01 91 59"), transport.writtenFrames.single())
    }
}
