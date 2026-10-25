package id.soilsense.modbus.soil

import id.soilsense.modbus.ModbusRtuClient

/** High-level access to the 7-in-1 soil probe. */
public class SoilSensor(private val client: ModbusRtuClient) {

    /** Reads registers 0x0000–0x0006 in one request, exactly like the manual's example frame. */
    public suspend fun read(slaveId: Int): SoilReading =
        SoilReadingDecoder.decode(
            client.readHoldingRegisters(
                slaveId = slaveId,
                startAddress = SoilSensorRegisters.MOISTURE,
                quantity = SoilSensorRegisters.MEASUREMENT_COUNT,
            ),
        )

    /** Reads the probe's configured slave ID through the 0xFF query address (only one probe may be on the bus). */
    public suspend fun queryAddress(): Int =
        client.readHoldingRegisters(
            slaveId = SoilSensorRegisters.BROADCAST_QUERY_ADDRESS,
            startAddress = SoilSensorRegisters.SLAVE_ID,
            quantity = 1,
        ).first()
}
