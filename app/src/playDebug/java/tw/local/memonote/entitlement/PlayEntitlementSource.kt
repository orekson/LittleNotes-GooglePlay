package tw.local.memonote.entitlement

import android.content.Context

/** Developer simulation is compiled exclusively into playDebug. */
object PlayEntitlementSource {
    private const val PREFS = "developer_pro_override"
    fun source(context: Context): EntitlementSource = EntitlementSource {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        EntitlementSnapshot(prefs.getBoolean("lifetime", false), prefs.getBoolean("subscription", false))
    }
    fun setSnapshot(context: Context, value: EntitlementSnapshot) {
        check(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("lifetime", value.lifetimePro).putBoolean("subscription", value.subscriptionActive).commit())
        EntitlementManager.refresh(context)
    }
}
