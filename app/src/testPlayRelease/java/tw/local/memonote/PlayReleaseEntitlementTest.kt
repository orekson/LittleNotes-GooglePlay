package tw.local.memonote
import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Test
import tw.local.memonote.entitlement.*
class PlayReleaseEntitlementTest {
    @Test fun releaseSourceHasTheCorrectPermanentPolicy() {
        val context=ContextWrapper(null)
        assertFalse(EntitlementManager.snapshot(context).isPro)
        assertEquals(EntitlementLevel.FREE,EntitlementManager.snapshot(context).level)
        assertFalse(PlayEntitlementSource::class.java.declaredMethods.any { it.name == "setSnapshot" })
    }
}
