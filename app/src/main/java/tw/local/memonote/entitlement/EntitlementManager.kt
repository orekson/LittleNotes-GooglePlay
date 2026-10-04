package tw.local.memonote.entitlement

import android.content.Context
import tw.local.memonote.cloud.CloudBackupJob

/** Flavor supplies the source. Screens and services use only feature policies. */
object EntitlementManager {
    fun snapshot(context: Context): EntitlementSnapshot = FlavorEntitlement.source(context).snapshot()
    fun policy(context: Context) = FeaturePolicy(snapshot(context))
    fun allows(context: Context, feature: PremiumFeature, operation: FeatureOperation) =
        policy(context).allows(feature, operation)
    fun require(context: Context, feature: PremiumFeature, operation: FeatureOperation) {
        if (!allows(context, feature, operation)) throw ProRequiredException(feature)
    }
    /** Also call this when a future Billing source changes, including expiry/refund. */
    fun refresh(context: Context) {
        if (!allows(context, PremiumFeature.CLOUD_AUTO_BACKUP, FeatureOperation.EXECUTE_EXISTING))
            CloudBackupJob.cancelAutomatic(context)
        tw.local.memonote.reminder.ReminderScheduler.safeSync(context)
        tw.local.memonote.widget.NoteWidgetProvider.updateAll(context)
    }
}
