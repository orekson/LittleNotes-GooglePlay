package tw.local.memonote.data

import org.json.JSONArray
import org.json.JSONObject

object CheckState {
    private fun array(raw: String)=runCatching { JSONObject(raw).optJSONArray("checks") }.getOrNull() ?: JSONArray()
    fun states(raw: String): Map<String,Boolean> = buildMap {
        val checks=array(raw)
        for(i in 0 until checks.length()) checks.optJSONObject(i)?.let { put(it.optString("id"),it.optBoolean("checked")) }
    }
    fun toggle(note: Note,id: String): Note? {
        val json=JSONObject(note.formatting); val checks=json.optJSONArray("checks") ?: return null
        for(i in 0 until checks.length()) {
            val item=checks.optJSONObject(i) ?: continue; val at=item.optInt("at",-1)
            if(item.optString("id")==id && at in note.body.indices && note.body[at]=='\uFFFC') {
                item.put("checked",!item.optBoolean("checked")); return note.copy(formatting=json.toString())
            }
        }
        return null
    }
    // Preserve widget changes made while an editor is open, unless the editor explicitly changed that checkbox.
    fun merge(original: Note,draft: Note,latest: Note): Note {
        val old=states(original.formatting); val current=states(latest.formatting)
        val json=JSONObject(draft.formatting); val checks=json.optJSONArray("checks") ?: return draft
        for(i in 0 until checks.length()) {
            val item=checks.optJSONObject(i) ?: continue; val id=item.optString("id")
            if(old.containsKey(id) && current.containsKey(id) && item.optBoolean("checked")==old[id]) item.put("checked",current[id])
        }
        return draft.copy(formatting=json.toString())
    }
}
