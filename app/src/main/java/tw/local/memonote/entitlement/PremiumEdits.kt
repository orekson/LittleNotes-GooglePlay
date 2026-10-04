package tw.local.memonote.entitlement

import org.json.JSONObject
import tw.local.memonote.data.Note

/** Save-time defense for settings, without changing SQLite, JSON, archives or restored content. */
object PremiumEdits {
    private val presets = setOf("paper", "sakura", "ocean", "night")
    fun deniedFeature(policy: FeaturePolicy, before: Note, after: Note): PremiumFeature? {
        if(!policy.allows(PremiumFeature.RECURRING_REMINDERS,FeatureOperation.MODIFY)) {
            val previous=tw.local.memonote.reminder.ReminderCodec.all(before).associateBy { it.id }
            if(tw.local.memonote.reminder.ReminderCodec.all(after).any { reminder ->
                reminder.rule.advanced && previous[reminder.id]?.let {
                    it.rule==reminder.rule && it.timeMillis==reminder.timeMillis
                } != true
            }) return PremiumFeature.RECURRING_REMINDERS
        }
        if (!policy.allows(PremiumFeature.ADVANCED_BACKGROUND, FeatureOperation.MODIFY) &&
            (before.background != after.background || before.fade != after.fade)) {
            val removesPhoto = after.background == before.background || after.background in presets
            val preservesOrRemovesFade = after.fade == before.fade || after.fade == 35
            if (!removesPhoto || !preservesOrRemovesFade) return PremiumFeature.ADVANCED_BACKGROUND
        }
        if (!policy.allows(PremiumFeature.ADVANCED_TEXT, FeatureOperation.MODIFY)) {
            val old = effects(before)
            if (effects(after).any { (effect, count) -> count > (old[effect] ?: 0) })
                return PremiumFeature.ADVANCED_TEXT
        }
        return null
    }
    private fun effects(note: Note): Map<Pair<String, Int?>, Int> {
        val array = runCatching { JSONObject(note.formatting).optJSONArray("styles") }.getOrNull()
            ?: return emptyMap()
        val counts = mutableMapOf<Pair<String, Int?>, Int>()
        for (i in 0 until array.length()) {
            val style = array.optJSONObject(i) ?: continue
            val start = style.optInt("start").coerceIn(0, note.body.length)
            val end = style.optInt("end").coerceIn(start, note.body.length)
            val color = if (style.has("color") && !style.isNull("color")) style.optInt("color") else null
            for (effect in listOf("rainbow", "glow")) if (style.optBoolean(effect)) {
                val key = effect to color
                counts[key] = (counts[key] ?: 0) + end - start
            }
        }
        return counts
    }
    fun requireAllowed(context: android.content.Context, before: Note, after: Note) {
        deniedFeature(EntitlementManager.policy(context), before, after)?.let { throw ProRequiredException(it) }
    }
}
