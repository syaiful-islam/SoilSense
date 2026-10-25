package id.soilsense.modbus

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.TestTimeSource

class ModbusRtuClientTest {

    private val timeSource = TestTimeSource()
    private val manualResponse = hex("01 03 0E 01 D0 01 4C 00 2C 00 5A 00 20 00 58 00 68 70 29")

    private fun client(transport: SerialTransport) = ModbusRtuClient(transport, timeSource = timeSource)

    @Test
    fun `reads registers from manual response`() = runTest {
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(manualResponse) }

        val registers = client(transport).readHoldingRegisters(1, 0, 7)

        assertContentEquals(intArrayOf(464, 332, 44, 90, 32, 88, 104), registers)
        assertContentEquals(hex("01 03 00 00 00 07 04 08"), transport.writtenFrames.single())
    }

    @Test
    fun `reassembles response split into small chunks`() = runTest {
        val transport = FakeSerialTransport(timeSource, chunkSize = 3).apply { enqueueReply(manualResponse) }

        val registers = client(transport).readHoldingRegisters(1, 0, 7)

        assertEquals(464, registers[0])
    }

    @Test
    fun `skips garbage bytes before the slave address`() = runTest {
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(hex("00 00") + manualResponse) }

        val registers = client(transport).readHoldingRegisters(1, 0, 7)

        assertEquals(104, registers[6])
    }

    @Test
    fun `stale input is discarded before sending a request`() = runTest {
        val transport = FakeSerialTransport(timeSource).apply {
            injectStaleInput(hex("01 03 02 00 05 78 47"))
            enqueueReply(manualResponse)
        }

        val registers = client(transport).readHoldingRegisters(1, 0, 7)

        assertEquals(1, transport.discardCount)
        assertEquals(464, registers[0])
    }

    @Test
    fun `silent slave times out`() = runTest {
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(null) }

        val error = assertFailsWith<ModbusException.ResponseTimeout> {
            client(transport).readHoldingRegisters(1, 0, 7)
        }
        assertEquals(0, error.receivedBytes)
    }

    @Test
    fun `truncated response times out with partial byte count`() = runTest {
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(manualResponse.copyOf(10)) }

        val error = assertFailsWith<ModbusException.ResponseTimeout> {
            client(transport).readHoldingRegisters(1, 0, 7)
        }
        assertEquals(10, error.receivedBytes)
    }

    @Test
    fun `corrupted response raises CRC mismatch`() = runTest {
        val corrupted = manualResponse.copyOf().also { it[4] = 0x00 }
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(corrupted) }

        assertFailsWith<ModbusException.CrcMismatch> { client(transport).readHoldingRegisters(1, 0, 7) }
    }

    @Test
    fun `exception response is surfaced with its code`() = runTest {
        val exceptionFrame = Crc16.appendCrc(hex("01 83 02"))
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(exceptionFrame) }

        val error = assertFailsWith<ModbusException.ExceptionResponse> {
            client(transport).readHoldingRegisters(1, 0, 9)
        }
        assertEquals(ModbusException.ExceptionResponse.ILLEGAL_DATA_ADDRESS, error.exceptionCode)
        assertEquals(ModbusFrames.FC_READ_HOLDING_REGISTERS, error.functionCode)
    }

    @Test
    fun `reply with wrong register count is rejected`() = runTest {
        val oneRegisterReply = Crc16.appendCrc(hex("01 03 02 00 01"))
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(oneRegisterReply) }

        assertFailsWith<ModbusException.UnexpectedResponse> { client(transport).readHoldingRegisters(1, 0, 2) }
    }

    @Test
    fun `reply from another function code is rejected`() = runTest {
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(Crc16.appendCrc(hex("01 04 02 00 01"))) }

        assertFailsWith<ModbusException.UnexpectedResponse> { client(transport).readHoldingRegisters(1, 0, 1) }
    }

    @Test
    fun `write single register accepts echo`() = runTest {
        val request = hex("01 06 00 04 00 20 C9 D3")
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(request) }

        client(transport).writeSingleRegister(1, 0x0004, 32)

        assertContentEquals(request, transport.writtenFrames.single())
    }

    @Test
    fun `slave id enquiry reads configured address`() = runTest {
        val transport = FakeSerialTransport(timeSource).apply { enqueueReply(hex("FF 03 02 00 01 50 50")) }

        val registers = client(transport).readHoldingRegisters(0xFF, 0x07D0, 1)

        assertContentEquals(intArrayOf(1), registers)
    }
}
