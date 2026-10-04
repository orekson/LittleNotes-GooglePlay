package tw.local.memonote.reminder

import org.json.JSONObject
import tw.local.memonote.data.Note

data class NoteReminder(val id: String, val at: Int, val timeMillis: Long, val rule: ReminderRule = ReminderRule())

object ReminderCodec {
    fun all(note: Note): List<NoteReminder> {
        if (note.isLocked) return emptyList()
        val json = runCatching { JSONObject(note.formatting) }.getOrNull() ?: return emptyList()
        val items = json.optJSONArray("reminders") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val at = item.optInt("at", -1)
                val id = item.optString("id")
                val time = item.optLong("time", 0)
                if (at in note.body.indices && note.body[at] == '\uFFFC' &&
                    id.matches(Regex("[a-f0-9-]{36}")) && time > 0) {
                    add(NoteReminder(id, at, time, ReminderRule.read(item)))
                }
            }
        }
    }

    fun line(note: Note, reminder: NoteReminder): String {
        val start = note.body.lastIndexOf('\n', reminder.at - 1).let { if (it < 0) 0 else it + 1 }
        val end = note.body.indexOf('\n', reminder.at).let { if (it < 0) note.body.length else it }
        return note.body.substring(start, end).replace('\uFFFC', ' ').trim()
            .ifBlank { note.title.trim() }
    }
}
