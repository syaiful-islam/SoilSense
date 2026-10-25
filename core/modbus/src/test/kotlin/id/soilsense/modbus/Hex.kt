package id.soilsense.modbus

/** Parses "01 03 0E" style hex strings used in the sensor manual. */
fun hex(text: String): ByteArray =
    text.split(' ').filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()
