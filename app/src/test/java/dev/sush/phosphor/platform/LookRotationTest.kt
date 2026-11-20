package dev.sush.phosphor.platform

import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class LookRotationTest {
    private val looks = listOf("a", "b", "c").map { SavedLook(it, it, WallpaperSettings()) }
    private val morning = ZonedDateTime.parse("2026-10-31T08:00:00-04:00[America/New_York]")

    @Test fun dailyUsesCalendarDaysAndSurvivesMissingLooksAndSkippedDays() {
        val schedule = LookRotation(RotationMode.DAILY, listOf("b", "gone", "a"), morning.toLocalDate().toEpochDay())
        assertEquals("b", schedule.resolve(looks, morning)?.look?.id)
        val tomorrow = schedule.resolve(looks, morning.plusDays(1))!!
        assertEquals("a", tomorrow.look.id)
        assertEquals(25 * 3600000L, tomorrow.endsAt - tomorrow.startsAt)
        assertEquals("a", schedule.resolve(looks, morning.plusDays(5))?.look?.id)
        assertNull(schedule.resolve(emptyList(), morning))
    }

    @Test fun skippedStartTimeTakesEffectAtTheFirstRealMinute() {
        val schedule = LookRotation(RotationMode.DAY_EVENING, listOf("a", "b"), dayMinute = 165, eveningMinute = 195)
        val afterJump = ZonedDateTime.parse("2026-03-08T03:00:00-04:00[America/New_York]")
        assertEquals("b", schedule.resolve(looks, afterJump.minusMinutes(1))?.look?.id)
        assertEquals("a", schedule.resolve(looks, afterJump)?.look?.id)
        assertEquals("b", schedule.resolve(looks, afterJump.plusMinutes(15))?.look?.id)
    }

    @Test fun eveningPersistsOverMidnightAndChangesAtTheConfiguredMinute() {
        val schedule = LookRotation(RotationMode.DAY_EVENING, listOf("a", "b"), dayMinute = 480, eveningMinute = 1230)
        assertEquals("b", schedule.resolve(looks, morning.minusMinutes(1))?.look?.id)
        assertEquals("a", schedule.resolve(looks, morning)?.look?.id)
        val evening = morning.withHour(20).withMinute(30)
        assertEquals(evening.toInstant().toEpochMilli(), schedule.resolve(looks, morning)?.endsAt)
        assertEquals("b", schedule.resolve(looks, evening)?.look?.id)
        assertEquals("b", schedule.resolve(looks, morning.plusDays(1).withHour(1))?.look?.id)
        assertEquals("a", schedule.resolve(looks.take(1), evening)?.look?.id)
    }
}
