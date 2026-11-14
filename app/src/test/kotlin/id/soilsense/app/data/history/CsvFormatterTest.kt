package id.soilsense.app.data.history

import id.soilsense.app.data.model.RecordedReading
import id.soilsense.app.testing.testReading
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals

class CsvFormatterTest {

    private val formatter = CsvFormatter(TimeZone.getTimeZone("Asia/Jakarta"))

    @Test
    fun `row uses ISO timestamp and dot decimals`() {
        val row = formatter.row(RecordedReading(id = 1, timestampMillis = 0, reading = testReading, name = "Lahan A"))

        assertEquals("1970-01-01T07:00:00+07:00,Lahan A,46.4,33.2,44,9.0,32,88,104,,", row)
    }

    @Test
    fun `decimal separator does not follow the device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("id-ID"))
            val row = formatter.row(RecordedReading(id = 1, timestampMillis = 0, reading = testReading, name = "Lahan A"))
            assertEquals("46.4", row.split(',')[2])
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `header has one column per row value`() {
        val row = formatter.row(RecordedReading(1, 0, testReading, ""))

        assertEquals(formatter.header().split(',').size, row.split(',').size)
        assertEquals(listOf("32", "88", "104", "", ""), row.split(',').takeLast(5))
    }

    @Test
    fun `salinity and TDS are exported when the probe reports them`() {
        val row = formatter.row(RecordedReading(1, 0, testReading.copy(salinityMgPerL = 24, tdsMgPerL = 22), ""))

        assertEquals(listOf("24", "22"), row.split(',').takeLast(2))
    }

    @Test
    fun `names with commas and quotes are quoted`() {
        val row = formatter.row(RecordedReading(1, 0, testReading, "Lahan \"A\", baris 3"))

        assertEquals("\"Lahan \"\"A\"\", baris 3\"", row.removePrefix("1970-01-01T07:00:00+07:00,").substringBefore(",46.4"))
    }

    @Test
    fun `names that look like formulas are neutralised`() {
        val row = formatter.row(RecordedReading(1, 0, testReading, "=HYPERLINK(1)"))

        assertEquals("'=HYPERLINK(1)", row.split(',')[1])
    }
}
