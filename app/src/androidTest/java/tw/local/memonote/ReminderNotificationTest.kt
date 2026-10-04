package tw.local.memonote

import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import tw.local.memonote.data.Note
import tw.local.memonote.data.NoteStore
import tw.local.memonote.reminder.ReminderScheduler
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {
    @Test fun dueReminderPostsItsWholeLine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = UUID.randomUUID().toString()
        val body = "Pay \uFFFC electricity and gas\nNot this line"
        val time = System.currentTimeMillis() + 5_000L
        val formatting = JSONObject().put("reminders", JSONArray().put(
            JSONObject().put("id", id).put("at", body.indexOf('\uFFFC')).put("time", time)
        )).toString()
        val noteId = NoteStore(context).use {
            it.save(Note(title = "Reminder delivery test", body = body, formatting = formatting))
        }
        try {
            ReminderScheduler.forceReschedule(context)
            var notification: Notification? = null
            val deadline = System.currentTimeMillis() + 20_000L
            while (notification == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(250)
                notification = manager.activeNotifications.firstOrNull { it.id == ReminderScheduler.notificationId(noteId, id) }
                    ?.notification
            }
            assertNotNull("Reminder did not post a notification", notification)
            val text = notification!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            assertEquals("Pay electricity and gas", text.replace(Regex("\\s+"), " "))
        } finally {
            NoteStore(context).use { it.delete(noteId) }
            manager.cancel(ReminderScheduler.notificationId(noteId, id))
            ReminderScheduler.syncAll(context)
        }
    }
}