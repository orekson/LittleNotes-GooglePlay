package tw.local.memonote.reminder

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.local.memonote.data.Note
import tw.local.memonote.widget.DateWidgetSchedule
import java.time.LocalDate

class ReminderFeatureTest {
    @Test fun reminderKeepsTheEntireLogicalLine() {
        val id = "e9d97fc1-79e7-4b58-8bfd-45f6d5fa4202"
        val body = "First line\nBuy \uFFFC milk and eggs\nThird line"
        val at = body.indexOf('\uFFFC')
        val formatting = JSONObject().put("reminders", JSONArray().put(
            JSONObject().put("id", id).put("at", at).put("time", 1_800_000_000_000L)
        )).toString()
        val note = Note(title = "Groceries", body = body, formatting = formatting)
        val reminder = ReminderCodec.all(note).single()
        assertEquals(id, reminder.id)
        assertEquals("Buy milk and eggs",
            ReminderCodec.line(note, reminder).replace(Regex("\\s+"), " "))
        assertTrue(ReminderCodec.all(note.copy(sealed = "encrypted")).isEmpty())
    }

    @Test fun dateSwitchUsesLatestDueAssignmentOnlyOnce() {
        val today = LocalDate.of(2026, 9, 25)
        val choices = mapOf(today.minusDays(3) to 7L, today to 8L, today.plusDays(1) to 9L)
        assertEquals(8L, DateWidgetSchedule.dueAssignment(choices, Long.MIN_VALUE, today)?.value)
        assertEquals(null,
            DateWidgetSchedule.dueAssignment(choices, today.toEpochDay(), today))
        assertEquals(9L,
            DateWidgetSchedule.dueAssignment(choices, today.toEpochDay(), today.plusDays(1))?.value)
    }
}