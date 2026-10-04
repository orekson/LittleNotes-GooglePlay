package tw.local.memonote.widget

import org.junit.Assert.*
import org.junit.Test
import tw.local.memonote.entitlement.EntitlementSnapshot
import tw.local.memonote.entitlement.FeaturePolicy

class WidgetAccessTest {
    @Test fun freeCanAddTwoButNotAThird() {
        val free = FeaturePolicy(EntitlementSnapshot())
        assertTrue(WidgetAccess.allowsNew(free, 0))
        assertTrue(WidgetAccess.allowsNew(free, 1))
        assertFalse(WidgetAccess.allowsNew(free, 2))
        assertFalse(WidgetAccess.allowsNew(free, 4))
    }

    @Test fun existingWidgetsAreGrandfatheredAndProIsUnlimited() {
        val free = FeaturePolicy(EntitlementSnapshot())
        assertTrue(WidgetAccess.allowsNew(free, 3, alreadyConfigured = true))
        assertTrue(WidgetAccess.allowsNew(FeaturePolicy(EntitlementSnapshot(subscriptionActive = true)), 5))
        assertTrue(WidgetAccess.allowsNew(FeaturePolicy(EntitlementSnapshot(lifetimePro = true)), 5))
    }
}
