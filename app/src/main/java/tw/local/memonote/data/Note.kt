package tw.local.memonote.data

import org.json.JSONObject

data class Note(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val formatting: String = "{}",
    val background: String = "paper",
    val fade: Int = 35,
    val updated: Long = System.currentTimeMillis(),
    val category: String = "",
    val sealed: String = ""
) {
    val isLocked get() = sealed.isNotBlank()
    val displayTitle get() = if (isLocked) "🔒 加密筆記" else title.trim().ifEmpty { "未命名筆記" }

    fun toJson(): String = JSONObject()
        .put("id", id).put("title", title).put("body", body)
        .put("formatting", formatting).put("background", background)
        .put("fade", fade).put("updated", updated)
        .put("category", category).put("sealed", sealed).toString()

    companion object {
        fun fromJson(raw: String): Note {
            val j = JSONObject(raw)
            return Note(
                id = j.optLong("id"),
                title = j.optString("title"),
                body = j.optString("body"),
                formatting = j.optString("formatting", "{}"),
                background = j.optString("background", "paper"),
                fade = j.optInt("fade", 35).coerceIn(0, 100),
                updated = j.optLong("updated"),
                category = j.optString("category"),
                sealed = j.optString("sealed")
            )
        }
    }
}
