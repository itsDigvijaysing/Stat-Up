package dev.statup.app.rpg

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A mission's 🔥 is consecutive local days completed, not a lifetime completion count. */
object MissionStreak {

    fun afterCompletion(previous: Int, lastCompletedDay: LocalDate?, today: LocalDate): Int =
        when (lastCompletedDay) {
            today -> previous.coerceAtLeast(1)
            today.minusDays(1) -> previous + 1
            else -> 1
        }

    // Judged at read time, so a missed day shows as broken even before anything rewrites the row.
    fun current(streak: Int, lastCompletedDay: LocalDate?, today: LocalDate): Int =
        if (lastCompletedDay != null && !lastCompletedDay.isBefore(today.minusDays(1))) streak else 0

    fun fromCompletionDays(days: Collection<LocalDate>, today: LocalDate): Int {
        val done = days.toSet()
        var day = when {
            today in done -> today
            today.minusDays(1) in done -> today.minusDays(1)
            else -> return 0
        }
        var run = 0
        while (day in done) {
            run++
            day = day.minusDays(1)
        }
        return run
    }

    fun dayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
