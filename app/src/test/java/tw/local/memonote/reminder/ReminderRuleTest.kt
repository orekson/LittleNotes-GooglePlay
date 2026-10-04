package tw.local.memonote.reminder

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderRuleTest {
    private val utc=ZoneId.of("UTC")
    private fun time(value: String)=LocalDateTime.parse(value).atZone(utc).toInstant().toEpochMilli()
    @Test fun singleReminderAndAdvanceHaveOneFutureTrigger() {
        val start=time("2026-09-29T18:00")
        assertEquals(start,ReminderRule(zone="UTC").next(start,start-1,utc)!!.fireTime)
        assertNull(ReminderRule(zone="UTC").next(start,start,utc))
        val rule=ReminderRule(leadMinutes=10,zone="UTC")
        assertEquals(start-600000,rule.next(start,start-600001,utc)!!.fireTime)
        assertNull(rule.next(start,start-600000,utc))
    }
    @Test fun dailyIntervalSkipsMissedOccurrencesWithoutDrifting() {
        val rule=ReminderRule(Repeat.DAILY,interval=3,zone="UTC")
        assertEquals(time("2026-10-02T09:00"),rule.next(time("2026-09-29T09:00"),time("2026-10-01T15:00"),utc)!!.time)
    }
    @Test fun weekdaysSkipWeekendsAndRespectInclusiveEndDate() {
        val start=time("2026-10-02T09:00")
        val rule=ReminderRule(Repeat.WEEKDAYS,until="2026-10-05",zone="UTC")
        assertEquals(time("2026-10-05T09:00"),rule.next(start,start,utc)!!.time)
        assertNull(rule.next(start,time("2026-10-05T09:00"),utc))
    }
    @Test fun monthlyLastDayReturnsToOriginalDayAfterFebruary() {
        val start=time("2028-01-31T09:00")
        val rule=ReminderRule(Repeat.MONTHLY,zone="UTC")
        val feb=rule.next(start,start,utc)!!.time
        assertEquals(time("2028-02-29T09:00"),feb)
        assertEquals(time("2028-03-31T09:00"),rule.next(start,feb,utc)!!.time)
    }
    @Test fun dailyWallTimeSurvivesDaylightSavingTransition() {
        val zone=ZoneId.of("America/New_York")
        val start=LocalDateTime.parse("2026-03-07T09:00").atZone(zone).toInstant().toEpochMilli()
        val next=ReminderRule(Repeat.DAILY,zone=zone.id).next(start,start,zone)!!.time
        assertEquals(23*3600000L,next-start)
        assertEquals(9,Instant.ofEpochMilli(next).atZone(zone).hour)
    }
    @Test fun weeklyIntervalAndTimezoneChangesKeepLocalAppointmentTime() {
        val start=time("2026-09-29T09:00")
        assertEquals(time("2026-10-13T09:00"),ReminderRule(Repeat.WEEKLY,interval=2,zone="UTC").next(start,start,utc)!!.time)
        val taipei=ZoneId.of("Asia/Taipei")
        val changed=ReminderRule(Repeat.DAILY,zone="UTC").next(start,start,taipei)!!
        assertEquals(9,Instant.ofEpochMilli(changed.time).atZone(taipei).hour)
    }
}
