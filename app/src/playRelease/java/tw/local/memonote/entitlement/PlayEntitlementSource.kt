package tw.local.memonote.entitlement

import android.content.Context

/** Until Billing is connected, release is always Free; no preferences or simulation entry point. */
object PlayEntitlementSource {
    fun source(context: Context): EntitlementSource = EntitlementSource { EntitlementSnapshot() }
}
