package tw.local.memonote

import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import tw.local.memonote.data.Note
import tw.local.memonote.data.NoteStore
import tw.local.memonote.reminder.ReminderCodec
import tw.local.memonote.rich.ReminderSpan
import tw.local.memonote.rich.RichText
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderPersistenceTest {
    @Test fun reminderMarkerSurvivesSaveAndKeepsItsLine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val body = SpannableStringBuilder("Pay \uFFFC electricity\nDo something else")
        val at = body.indexOf('\uFFFC')
        val id = UUID.randomUUID().toString()
        val time = System.currentTimeMillis() + 3_600_000L
        body.setSpan(ReminderSpan(id, time), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val note = Note(title = "Bill", body = body.toString(), formatting = RichText.encode(body))
        val savedId = NoteStore(context).use { it.save(note) }
        try {
            val saved = NoteStore(context).use { it.find(savedId) }
            assertNotNull(saved)
            val reminder = ReminderCodec.all(saved!!).single()
            assertEquals(time, reminder.timeMillis)
            assertEquals("Pay electricity",
                ReminderCodec.line(saved, reminder).replace(Regex("\\s+"), " "))
            val decoded = RichText.decode(context, saved.body, saved.formatting, 76)
            assertEquals(id,
                decoded.getSpans(0, decoded.length, ReminderSpan::class.java).single().id)
        } finally {
            NoteStore(context).use { it.delete(savedId) }
        }
    }
}