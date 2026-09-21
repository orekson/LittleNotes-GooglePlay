package tw.local.memonote.data

import org.json.JSONObject

data class Note(
    val id: Long = 0, val title: String = "", val body: String = "", val formatting: String = "{}",
    val background: String = "paper", val fade: Int = 35, val updated: Long = System.currentTimeMillis()
) {
    val displayTitle get() = title.trim().ifEmpty { "未命名筆記" }
    fun toJson() = JSONObject().put("id",id).put("title",title).put("body",body).put("formatting",formatting)
        .put("background",background).put("fade",fade).put("updated",updated).toString()
    companion object {
        fun fromJson(raw: String): Note { val j=JSONObject(raw); return Note(j.optLong("id"),j.optString("title"),j.optString("body"),j.optString("formatting","{}"),j.optString("background","paper"),j.optInt("fade",35).coerceIn(0,100),j.optLong("updated")) }
    }
}
