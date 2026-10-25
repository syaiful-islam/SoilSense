package id.soilsense.app.data.model

import id.soilsense.modbus.soil.SoilReading

/** Outcome of one poll of the probe. */
sealed interface SensorSample {
    val timestampMillis: Long

    data class Success(val reading: SoilReading, override val timestampMillis: Long) : SensorSample

    data class Failure(val error: SensorReadError, override val timestampMillis: Long) : SensorSample
}

/** Why a poll failed; each case maps to a different troubleshooting hint in the UI. */
sealed interface SensorReadError {

    /** Nothing came back: wrong slave ID or baud rate, A/B swapped, or the probe is unpowered. */
    data object NoResponse : SensorReadError

    /** Bytes arrived but failed CRC or framing checks: noise, wrong baud rate or bad wiring. */
    data object CorruptedResponse : SensorReadError

    /** The probe answered with a Modbus exception code. */
    data class DeviceException(val code: Int) : SensorReadError

    /** The USB link dropped while polling. */
    data object ConnectionLost : SensorReadError

    data object NotConnected : SensorReadError
}
