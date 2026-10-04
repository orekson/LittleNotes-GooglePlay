package tw.local.memonote.data

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** One-time in-memory preview handoff for an unlocked encrypted note. */
object PreviewCache {
    private val notes = ConcurrentHashMap<String, Note>()
    fun put(note: Note): String {
        val key = UUID.randomUUID().toString()
        notes[key] = note
        return key
    }
    fun take(key: String): Note? = notes.remove(key)
}
