package tw.local.memonote.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import tw.local.memonote.entitlement.*
import tw.local.memonote.R

object DebugEntitlementControls {
    fun add(activity: Activity, column: LinearLayout, changed: () -> Unit) {
        column.addView(Ui.button(activity, activity.getString(R.string.debug_entitlement_switch)) {
            val options = arrayOf("Free", "Pro subscription", "Lifetime Pro")
            AlertDialog.Builder(activity).setTitle(activity.getString(R.string.debug_entitlement_title))
                .setSingleChoiceItems(options, EntitlementManager.snapshot(activity).level.ordinal) { dialog, index ->
                    PlayEntitlementSource.setSnapshot(activity, when (index) {
                        1 -> EntitlementSnapshot(subscriptionActive = true)
                        2 -> EntitlementSnapshot(lifetimePro = true)
                        else -> EntitlementSnapshot()
                    })
                    dialog.dismiss(); changed()
                }.setNegativeButton(AppLanguage.text(activity, "取消"), null).show()
        })
    }
}
