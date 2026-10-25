package id.soilsense.app.data

import javax.inject.Inject

/** Wall-clock time, injectable so timestamps are deterministic in tests. */
fun interface WallClock {
    fun nowMillis(): Long
}

class SystemWallClock @Inject constructor() : WallClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
