package tw.local.memonote.widget

import android.content.Context
import android.graphics.Color
import tw.local.memonote.entitlement.EntitlementManager
import tw.local.memonote.entitlement.FeatureOperation
import tw.local.memonote.entitlement.PremiumFeature
import tw.local.memonote.entitlement.ProRequiredException
import tw.local.memonote.model.NoteColors

/** A per-widget override; absent values keep the original note background behavior. */
object WidgetAppearance {
    const val FOLLOW_NOTE = "follow_note"
    val presets = setOf(FOLLOW_NOTE, "paper", "sakura", "ocean", "night")
    private const val PREFS = "widget_appearance"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selected(context: Context, widgetId: Int): String =
        prefs(context).getString(widgetId.toString(), FOLLOW_NOTE) ?: FOLLOW_NOTE

    fun background(context: Context, widgetId: Int, noteBackground: String): String =
        selected(context, widgetId).takeUnless { it == FOLLOW_NOTE } ?: noteBackground

    fun textColor(background: String): Int =
        if (background == "night" ||
            NoteColors.backgroundColor(background)?.let { Color.luminance(it) < 0.35 } == true)
            Color.WHITE else 0xff302b3e.toInt()

    fun requireAllowed(context: Context, widgetId: Int, selection: String) {
        require(selection in presets || NoteColors.backgroundColor(selection) != null)
        if (selection !in presets && selection != selected(context, widgetId) &&
            !EntitlementManager.allows(context, PremiumFeature.WIDGET_BACKGROUND_COLOR,
                FeatureOperation.MODIFY))
            throw ProRequiredException(PremiumFeature.WIDGET_BACKGROUND_COLOR)
    }

    fun set(context: Context, widgetId: Int, selection: String) {
        requireAllowed(context, widgetId, selection)
        check(prefs(context).edit().putString(widgetId.toString(), selection).commit())
    }

    fun forget(context: Context, widgetIds: IntArray) {
        val editor = prefs(context).edit()
        widgetIds.forEach { editor.remove(it.toString()) }
        editor.apply()
    }
}
