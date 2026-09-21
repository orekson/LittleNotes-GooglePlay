package tw.local.memonote.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

object DraftFiles {
    private fun file(context: Context,key: String): File {
        require(key.matches(Regex("[a-zA-Z0-9-]+")))
        return File(File(context.filesDir,"drafts").apply { mkdirs() },"$key.json")
    }
    fun write(context: Context,key: String,original: Note,draft: Note) {
        val storage=AtomicFile(file(context,key)); val output=storage.startWrite()
        try { output.write(JSONObject().put("original",JSONObject(original.toJson())).put("draft",JSONObject(draft.toJson())).toString().toByteArray(Charsets.UTF_8)); storage.finishWrite(output) }
        catch(e: Exception) { storage.failWrite(output); throw e }
    }
    fun read(context: Context,key: String): Pair<Note,Note> {
        val json=JSONObject(AtomicFile(file(context,key)).readFully().toString(Charsets.UTF_8))
        return Note.fromJson(json.getJSONObject("original").toString()) to Note.fromJson(json.getJSONObject("draft").toString())
    }
    fun delete(context: Context,key: String) { AtomicFile(file(context,key)).delete() }
}
