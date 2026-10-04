package tw.local.memonote.entitlement

import org.junit.Assert.*
import org.junit.Test
import tw.local.memonote.data.Note

class FeaturePolicyTest {
    @Test fun lifetimeAndSubscriptionIndependentlyUnlockPro() {
        assertFalse(EntitlementSnapshot().isPro)
        assertTrue(EntitlementSnapshot(subscriptionActive = true).isPro)
        assertTrue(EntitlementSnapshot(lifetimePro = true).isPro)
        assertTrue(EntitlementSnapshot(true, true).isPro)
    }
    @Test fun freeRetainsReadsRemovalsAndExistingSchedulesButStopsAutomaticBackups() {
        val policy = FeaturePolicy(EntitlementSnapshot())
        PremiumFeature.entries.forEach { feature ->
            assertFalse(policy.allows(feature, FeatureOperation.CREATE))
            assertFalse(policy.allows(feature, FeatureOperation.MODIFY))
            assertEquals(feature!=PremiumFeature.VERSION_HISTORY,policy.allows(feature, FeatureOperation.VIEW))
            assertTrue(policy.allows(feature, FeatureOperation.REMOVE))
            assertEquals(feature !in setOf(PremiumFeature.CLOUD_AUTO_BACKUP, PremiumFeature.CLOUD_AUTO_SYNC,
                PremiumFeature.RECURRING_REMINDERS,PremiumFeature.ADVANCED_WIDGET_CUSTOMIZATION,PremiumFeature.VERSION_HISTORY),
                policy.allows(feature, FeatureOperation.EXECUTE_EXISTING))
        }
    }
    @Test fun subscriptionAndLifetimeAllowEveryOperation() {
        listOf(EntitlementSnapshot(subscriptionActive = true), EntitlementSnapshot(lifetimePro = true))
            .forEach { state -> PremiumFeature.entries.forEach { feature ->
                FeatureOperation.entries.forEach { assertTrue(FeaturePolicy(state).allows(feature, it)) }
            } }
    }
    @Test fun expiryPreservesBackgroundButRejectsChangesAndAllowsRemoval() {
        val old = Note(background = "file:photo.png", fade = 61)
        val free = FeaturePolicy(EntitlementSnapshot())
        assertNull(PremiumEdits.deniedFeature(free, old, old.copy(body = "ordinary edits")))
        assertEquals(PremiumFeature.ADVANCED_BACKGROUND,
            PremiumEdits.deniedFeature(free, old, old.copy(background = "file:new.png")))
        assertEquals(PremiumFeature.ADVANCED_BACKGROUND,
            PremiumEdits.deniedFeature(free, old, old.copy(fade = 62)))
        assertNull(PremiumEdits.deniedFeature(free, old, old.copy(background = "paper", fade = 35)))
    }
    @Test fun freeCanCreateAndChangeSingleReminders() {
        fun note(reminders: String, body: String = "\uFFFC message") = Note(body = body,
            formatting = """{"reminders":[$reminders]}""")
        val old = note("""{"id":"old","at":0,"time":1000}""")
        val free = FeaturePolicy(EntitlementSnapshot())
        assertNull(PremiumEdits.deniedFeature(free, old, old.copy(title = "edited")))
        assertNull(PremiumEdits.deniedFeature(free, old, note("", "message")))
        assertNull(PremiumEdits.deniedFeature(free, old,
            note("""{"id":"old","at":0,"time":2000}""")))
        assertNull(PremiumEdits.deniedFeature(free, old,
            note("""{"id":"new","at":0,"time":1000}""")))
    }
    @Test fun effectsSurviveOrdinaryEditsButCannotGrowOrChangeColorAfterExpiry() {
        val free=FeaturePolicy(EntitlementSnapshot())
        val old=Note(body="abc",formatting="""{"styles":[{"start":0,"end":3,"glow":true,"rainbow":true,"color":7}]}""")
        assertNull(PremiumEdits.deniedFeature(free,old,old.copy(title="edit")))
        assertNull(PremiumEdits.deniedFeature(free,old,old.copy(formatting="""{"styles":[{"start":0,"end":3,"glow":true,"color":7}]}""")))
        assertEquals(PremiumFeature.ADVANCED_TEXT,PremiumEdits.deniedFeature(free,old,old.copy(body="abcd",formatting="""{"styles":[{"start":0,"end":4,"glow":true,"color":7}]}""")))
        assertEquals(PremiumFeature.ADVANCED_TEXT,PremiumEdits.deniedFeature(free,old,old.copy(formatting="""{"styles":[{"start":0,"end":3,"glow":true,"color":8}]}""")))
    }
    @Test fun freeNewNotesAndPresetBackgroundsAreUnrestricted() {
        val free = FeaturePolicy(EntitlementSnapshot())
        assertNull(PremiumEdits.deniedFeature(free, Note(), Note(title = "new", category = "work")))
        assertNull(PremiumEdits.deniedFeature(free, Note(), Note(background = "night")))
    }
    @Test fun freeCannotCreateOrChangeRecurringOrAdvanceRemindersButCanRemoveThem() {
        fun note(repeat: String,time: Long=1800000000000,lead: Int=0)=Note(body="\uFFFC pay bill",
            formatting="""{"reminders":[{"id":"e9d97fc1-79e7-4b58-8bfd-45f6d5fa4202","at":0,"time":$time,"repeat":"$repeat","leadMinutes":$lead,"zone":"UTC"}]}""")
        val free=FeaturePolicy(EntitlementSnapshot())
        val single=note("NONE"); val daily=note("DAILY")
        assertNull(PremiumEdits.deniedFeature(free,Note(),single))
        assertEquals(PremiumFeature.RECURRING_REMINDERS,PremiumEdits.deniedFeature(free,single,daily))
        assertEquals(PremiumFeature.RECURRING_REMINDERS,PremiumEdits.deniedFeature(free,single,note("NONE",lead=10)))
        assertNull(PremiumEdits.deniedFeature(free,daily,daily.copy(title="plain edit")))
        assertEquals(PremiumFeature.RECURRING_REMINDERS,PremiumEdits.deniedFeature(free,daily,note("DAILY",1800000001000)))
        assertNull(PremiumEdits.deniedFeature(free,daily,single))
        assertNull(PremiumEdits.deniedFeature(free,daily,Note(body="pay bill")))
    }
}
