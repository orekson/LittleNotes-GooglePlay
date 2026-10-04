package tw.local.memonote.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import tw.local.memonote.entitlement.EntitlementManager
import tw.local.memonote.entitlement.FeatureOperation
import tw.local.memonote.entitlement.FeaturePolicy
import tw.local.memonote.entitlement.PremiumFeature
import tw.local.memonote.entitlement.ProRequiredException

/** Only new widget bindings are limited; already configured widgets remain usable. */
object WidgetAccess {
    const val FREE_LIMIT = 2

    fun allowsNew(policy: FeaturePolicy, otherWidgetCount: Int, alreadyConfigured: Boolean = false): Boolean =
        alreadyConfigured || otherWidgetCount < FREE_LIMIT ||
            policy.allows(PremiumFeature.UNLIMITED_WIDGETS, FeatureOperation.CREATE)

    private fun activeIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))

    fun canRequestNew(context: Context): Boolean =
        allowsNew(EntitlementManager.policy(context), activeIds(context).size)

    fun canConfigure(context: Context, widgetId: Int): Boolean =
        allowsNew(EntitlementManager.policy(context), activeIds(context).count { it != widgetId },
            NoteWidgetProvider.noteId(context, widgetId) != 0L)

    fun requireCanConfigure(context: Context, widgetId: Int) {
        if (!canConfigure(context, widgetId)) throw ProRequiredException(PremiumFeature.UNLIMITED_WIDGETS)
    }
}
