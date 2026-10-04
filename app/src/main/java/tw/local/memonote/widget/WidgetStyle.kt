package tw.local.memonote.widget

import android.content.Context
import org.json.JSONObject
import tw.local.memonote.entitlement.*

data class WidgetStyle(
    val corner: Int = 24, val padding: Int = 12, val lineGap: Int = 5,
    val font: String = "sans-serif", val fontSize: Int = 16,
    val opacity: Int = 100, val showImages: Boolean = true
) {
    fun normalized() = copy(corner = corner.takeIf { it in corners } ?: 24,
        padding = padding.coerceIn(4, 32), lineGap = lineGap.coerceIn(0, 20),
        font = font.takeIf { it in fonts } ?: "sans-serif", fontSize = fontSize.coerceIn(12, 28),
        opacity = opacity.coerceIn(10, 100))
    fun json(): String = JSONObject().put("corner",corner).put("padding",padding)
        .put("lineGap",lineGap).put("font",font).put("fontSize",fontSize)
        .put("opacity",opacity).put("showImages",showImages).toString()
    companion object {
        val corners = listOf(0, 12, 24, 36)
        val fonts = listOf("sans-serif", "serif", "monospace", "sans-serif-light")
        fun parse(raw: String?): WidgetStyle = runCatching {
            val j = JSONObject(raw ?: "{}")
            WidgetStyle(j.optInt("corner",24),j.optInt("padding",12),j.optInt("lineGap",5),
                j.optString("font","sans-serif"),j.optInt("fontSize",16),
                j.optInt("opacity",100),j.optBoolean("showImages",true)).normalized()
        }.getOrDefault(WidgetStyle())
        private fun prefs(c: Context) = c.getSharedPreferences("widget_style",Context.MODE_PRIVATE)
        fun stored(c: Context, id: Int) = parse(prefs(c).getString(id.toString(),null))
        fun effective(c: Context, id: Int) = if (EntitlementManager.allows(c,
            PremiumFeature.ADVANCED_WIDGET_CUSTOMIZATION,FeatureOperation.EXECUTE_EXISTING)) stored(c,id) else WidgetStyle()
        fun set(c: Context, id: Int, value: WidgetStyle) {
            val clean = value.normalized()
            if (clean != stored(c,id) && clean != WidgetStyle())
                EntitlementManager.require(c,PremiumFeature.ADVANCED_WIDGET_CUSTOMIZATION,FeatureOperation.MODIFY)
            check(prefs(c).edit().putString(id.toString(),clean.json()).commit())
        }
        fun forget(c: Context, ids: IntArray) {
            val edit=prefs(c).edit(); ids.forEach { edit.remove(it.toString()) }; edit.apply()
        }
    }
}
