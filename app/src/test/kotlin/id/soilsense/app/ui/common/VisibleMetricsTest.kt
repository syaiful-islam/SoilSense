package id.soilsense.app.ui.common

import id.soilsense.app.testing.testReading
import org.junit.Test
import kotlin.test.assertEquals

class VisibleMetricsTest {

    @Test
    fun `all nine tiles before the first reading`() {
        assertEquals(SoilMetric.entries, visibleMetrics(null))
    }

    @Test
    fun `salinity and TDS are hidden when the probe does not report them`() {
        val shown = visibleMetrics(testReading)

        assertEquals(7, shown.size)
        assertEquals(false, SoilMetric.SALINITY in shown || SoilMetric.TDS in shown)
    }

    @Test
    fun `salinity and TDS are shown when reported`() {
        val shown = visibleMetrics(testReading.copy(salinityMgPerL = 24, tdsMgPerL = 22))

        assertEquals(SoilMetric.entries, shown)
    }
}
