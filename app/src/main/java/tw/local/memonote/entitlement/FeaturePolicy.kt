package tw.local.memonote.entitlement

/** A Billing adapter may supply both facts; either one grants Pro. Never stored in note data. */
data class EntitlementSnapshot(val lifetimePro: Boolean = false, val subscriptionActive: Boolean = false) {
    val isPro: Boolean get() = lifetimePro || subscriptionActive
    val level: EntitlementLevel get() = when {
        lifetimePro -> EntitlementLevel.LIFETIME_PRO
        subscriptionActive -> EntitlementLevel.SUBSCRIPTION_ACTIVE
        else -> EntitlementLevel.FREE
    }
}
enum class EntitlementLevel { FREE, SUBSCRIPTION_ACTIVE, LIFETIME_PRO }
fun interface EntitlementSource { fun snapshot(): EntitlementSnapshot }
enum class PremiumFeature {
    ADVANCED_TEXT, ADVANCED_BACKGROUND, WIDGET_DATE_SCHEDULE, CLOUD_AUTO_BACKUP,
    UNLIMITED_WIDGETS, ADVANCED_WIDGET_CUSTOMIZATION, RECURRING_REMINDERS,
    CLOUD_AUTO_SYNC, PREMIUM_THEMES, VERSION_HISTORY, WIDGET_BACKGROUND_COLOR
}
enum class FeatureOperation { VIEW, CREATE, MODIFY, REMOVE, EXECUTE_EXISTING }

class FeaturePolicy(private val entitlement: EntitlementSnapshot) {
    fun allows(feature: PremiumFeature, operation: FeatureOperation): Boolean = when (operation) {
        FeatureOperation.REMOVE -> true
        FeatureOperation.VIEW -> feature != PremiumFeature.VERSION_HISTORY || entitlement.isPro
        FeatureOperation.EXECUTE_EXISTING ->
            feature !in setOf(PremiumFeature.CLOUD_AUTO_BACKUP, PremiumFeature.CLOUD_AUTO_SYNC,
                PremiumFeature.RECURRING_REMINDERS,PremiumFeature.ADVANCED_WIDGET_CUSTOMIZATION,
                PremiumFeature.VERSION_HISTORY) || entitlement.isPro
        FeatureOperation.CREATE, FeatureOperation.MODIFY -> entitlement.isPro
    }
}
class ProRequiredException(val feature: PremiumFeature) : IllegalStateException("LittleNotes Pro required")
