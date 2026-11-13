package id.soilsense.app.ui.common

import org.junit.Test
import kotlin.test.assertEquals

class PhClassTest {

    @Test
    fun `boundaries follow the agronomic neutral range 6_5 to 7_5`() {
        assertEquals(PhClass.ACIDIC, PhClass.of(6.4))
        assertEquals(PhClass.NEUTRAL, PhClass.of(6.5))
        assertEquals(PhClass.NEUTRAL, PhClass.of(7.5))
        assertEquals(PhClass.ALKALINE, PhClass.of(7.6))
    }
}
