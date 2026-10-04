package tw.local.memonote.entitlement

import android.content.Context

object FlavorEntitlement {
    val showsProUi = true
    fun source(context: Context): EntitlementSource = PlayEntitlementSource.source(context)
}
