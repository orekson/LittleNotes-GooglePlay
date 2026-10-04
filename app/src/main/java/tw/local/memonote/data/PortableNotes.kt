package tw.local.memonote.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A self-contained note snapshot with media embedded under portable IDs. */
object PortableNotes {
    private const val VERSION = 1
    private const val MAX_MEDIA_COUNT = 1000
    private const val MAX_MEDIA_BYTES = 20 * 1024 * 1024
    private const val MAX_NOTE_MEDIA_BYTES = 128L * 1024 * 1024

    fun refs(note: Note): Set<String> {
        val found = mutableSetOf<String>()
        if (note.background.contains(':')) found += note.background
        val formatting = try { JSONObject(note.formatting) } catch (_: Exception) { return found }
        val stickers = formatting.optJSONArray("stickers") ?: JSONArray()
        for (index in 0 until stickers.length()) {
            val ref = stickers.optJSONObject(index)?.optString("ref").orEmpty()
            if (ref.contains(':')) found += ref
        }
        return found
    }

    private fun rewrite(note: Note, map: (String) -> String): Note {
        val formatting = try { JSONObject(note.formatting) } catch (_: Exception) { JSONObject() }
        val stickers = formatting.optJSONArray("stickers") ?: JSONArray()
        for (index in 0 until stickers.length()) {
            val item = stickers.optJSONObject(index) ?: continue
            val ref = item.optString("ref")
            if (ref.isNotBlank()) item.put("ref", map(ref))
        }
        val background = if (note.background.contains(':')) map(note.background) else note.background
        return note.copy(formatting = formatting.toString(), background = background)
    }

    fun pack(context: Context, note: Note): String {
        require(!note.isLocked)
        val media = JSONObject()
        val ids = mutableMapOf<String, String>()
        var total = 0L
        val portable = rewrite(note) { ref ->
            if (!ref.startsWith("file:") && !ref.startsWith("asset:") && !ref.startsWith("vault:")) {
                ref
            } else {
                ids.getOrPut(ref) {
                    require(ids.size < MAX_MEDIA_COUNT) { "附件數量過多" }
                    val bytes = ImageFiles.readBytes(context, ref)
                    total += bytes.size
                    require(total <= MAX_NOTE_MEDIA_BYTES) { "單篇筆記附件超過 128 MB" }
                    val id = "media:" + UUID.randomUUID()
                    media.put(id, Base64.encodeToString(bytes, Base64.NO_WRAP))
                    id
                }
            }
        }
        return JSONObject().put("version", VERSION)
            .put("note", JSONObject(portable.copy(sealed = "").toJson()))
            .put("media", media).toString()
    }

    /** Compare logical content without random portable media IDs or save timestamps. */
    internal fun sameContent(first: String,second: String): Boolean {
        fun canonical(value: Any?): String = when(value) {
            is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",","{","}") {
                JSONObject.quote(it)+":"+canonical(value.get(it))
            }
            is JSONArray -> (0 until value.length()).joinToString(",","[","]") { canonical(value.get(it)) }
            is String -> JSONObject.quote(value)
            null,JSONObject.NULL -> "null"
            else -> value.toString()
        }
        fun content(raw: String): String {
            val container=JSONObject(raw)
            require(container.getInt("version")==VERSION)
            val media=container.getJSONObject("media")
            val note=rewrite(Note.fromJson(container.getJSONObject("note").toString())) { ref ->
                if(!ref.startsWith("media:")) ref else {
                    val digest=java.security.MessageDigest.getInstance("SHA-256")
                        .digest(media.getString(ref).toByteArray(Charsets.UTF_8))
                    "media-sha256:"+digest.joinToString("") { "%02x".format(it) }
                }
            }.copy(id=0,updated=0,sealed="")
            return canonical(JSONObject(note.toJson()).put("formatting",JSONObject(note.formatting)))
        }
        return content(first)==content(second)
    }

    private fun unpack(raw: String, map: (String, ByteArray) -> String): Note {
        val container = JSONObject(raw)
        require(container.getInt("version") == VERSION) { "筆記附件格式不支援" }
        val note = Note.fromJson(container.getJSONObject("note").toString())
        require(!note.isLocked) { "筆記附件格式不正確" }
        val media = container.getJSONObject("media")
        require(media.length() <= MAX_MEDIA_COUNT) { "附件數量過多" }
        val ids = mutableMapOf<String, String>()
        var total = 0L
        val keys = media.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            require(id.matches(Regex("media:[a-f0-9-]{36}"))) { "附件識別碼不正確" }
            val encoded = media.getString(id)
            require(encoded.length <= MAX_MEDIA_BYTES * 4 / 3 + 8) { "附件過大" }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(bytes.isNotEmpty() && bytes.size <= MAX_MEDIA_BYTES) { "附件過大或無效" }
            total += bytes.size
            require(total <= MAX_NOTE_MEDIA_BYTES) { "單篇筆記附件超過 128 MB" }
            ids[id] = map(id, bytes)
        }
        return rewrite(note) { ref ->
            when {
                ref.startsWith("media:") -> ids[ref] ?: error("備份缺少筆記附件")
                ref.startsWith("file:") || ref.startsWith("vault:") || ref.startsWith("asset:") ->
                    error("備份含有未封存的附件")
                else -> ref
            }
        }
    }

    fun openInMemory(raw: String, id: Long, session: String): Note {
        try {
            return unpack(raw) { _, bytes -> VaultMedia.put(session, bytes) }
                .copy(id = id, sealed = "")
        } catch (e: Exception) {
            VaultMedia.clear(session)
            throw e
        }
    }

    fun restoreToDirectory(raw: String, directory: File): Note {
        require(directory.isDirectory)
        return unpack(raw) { _, bytes ->
            val name = "img_" + UUID.randomUUID() + ".png"
            val target = File(directory, name)
            target.outputStream().use { it.write(bytes) }
            "file:" + name
        }.copy(id = 0, sealed = "")
    }
}
