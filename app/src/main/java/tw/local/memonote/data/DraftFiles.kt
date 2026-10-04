package tw.local.memonote.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

object DraftFiles {
    private fun directory(context: Context): File = File(context.filesDir, "drafts").apply { mkdirs() }

    private fun file(context: Context, key: String, encrypted: Boolean = false): File {
        require(key.matches(Regex("[a-zA-Z0-9-]+")))
        return File(directory(context), key + if (encrypted) ".vault" else ".json")
    }

    fun write(context: Context, key: String, original: Note, draft: Note) {
        val storage = AtomicFile(file(context, key))
        val output = storage.startWrite()
        try {
            output.write(
                JSONObject().put("original", JSONObject(original.toJson()))
                    .put("draft", JSONObject(draft.toJson()))
                    .toString().toByteArray(Charsets.UTF_8)
            )
            storage.finishWrite(output)
        } catch (e: Exception) {
            storage.failWrite(output)
            throw e
        }
    }

    fun read(context: Context, key: String): Pair<Note, Note> {
        val json = JSONObject(AtomicFile(file(context, key)).readFully().toString(Charsets.UTF_8))
        return Note.fromJson(json.getJSONObject("original").toString()) to
            Note.fromJson(json.getJSONObject("draft").toString())
    }

    fun writeProtected(context: Context, key: String, original: Note, draft: Note, password: CharArray) {
        val payload = JSONObject()
            .put("version", 1)
            .put("original", JSONObject(PortableNotes.pack(context, original)))
            .put("draft", JSONObject(PortableNotes.pack(context, draft)))
            .toString().toByteArray(Charsets.UTF_8)
        val encrypted = PasswordCrypto.encryptBytes(payload, password, PasswordCrypto.NOTE_MAGIC)
        val storage = AtomicFile(file(context, key, true))
        val output = storage.startWrite()
        try {
            output.write(encrypted)
            storage.finishWrite(output)
        } catch (e: Exception) {
            storage.failWrite(output)
            throw e
        }
    }

    fun readProtected(
        context: Context,
        key: String,
        noteId: Long,
        password: CharArray,
        session: String
    ): Pair<Note, Note> {
        val encrypted = AtomicFile(file(context, key, true)).readFully()
        val json = JSONObject(
            PasswordCrypto.decryptBytes(encrypted, password, PasswordCrypto.NOTE_MAGIC)
                .toString(Charsets.UTF_8)
        )
        require(json.getInt("version") == 1)
        return PortableNotes.openInMemory(json.getJSONObject("original").toString(), noteId, session) to
            PortableNotes.openInMemory(json.getJSONObject("draft").toString(), noteId, session)
    }

    fun hasProtected(context: Context, key: String): Boolean = file(context, key, true).exists()

    fun clearForNote(context: Context, noteId: Long) {
        directory(context).listFiles { f -> f.extension == "json" }.orEmpty().forEach { draft ->
            try {
                val original = JSONObject(draft.readText()).optJSONObject("original")
                if (original?.optLong("id") == noteId) draft.delete()
            } catch (_: Exception) {
                // An unreadable old draft cannot be associated with this note.
            }
        }
    }

    fun delete(context: Context, key: String) {
        AtomicFile(file(context, key)).delete()
        AtomicFile(file(context, key, true)).delete()
    }
}
