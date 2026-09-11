package com.google.mediapipe.examples.poselandmarker.model

/** Converts the cumulative hardware counter into a daily total without losing reboot history. */
class DailyStepCounter(
    private var day: String,
    initialTotal: Int = 0,
    private var lastRaw: Int = -1
) {
    var total: Int = initialTotal.coerceAtLeast(0)
        private set

    fun record(currentDay: String, raw: Int): Int {
        if (raw < 0) return total
        if (day != currentDay) {
            day = currentDay
            total = 0
            lastRaw = -1
        }
        if (lastRaw >= 0) {
            val delta = if (raw >= lastRaw) raw - lastRaw else raw
            total = (total.toLong() + delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        lastRaw = raw
        return total
    }
}
