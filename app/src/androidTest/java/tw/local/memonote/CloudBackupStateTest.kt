package tw.local.memonote

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tw.local.memonote.cloud.CloudBackupState
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class CloudBackupStateTest {
    @Test fun fiveCharacterPasswordRoundTripsWithoutPlaintextPreferences() {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val isolated = object : ContextWrapper(real) {
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = real.packageName + ".cloudtest"
            override fun getSharedPreferences(name: String, mode: Int) =
                real.getSharedPreferences("cloudtest-" + name, mode)
        }
        val password = "abcde".toCharArray()
        try {
            CloudBackupState.connect(isolated, "test@example.com", password)
            assertTrue(CloudBackupState.connected(isolated))
            assertEquals(tw.local.memonote.entitlement.EntitlementManager.allows(isolated,
                tw.local.memonote.entitlement.PremiumFeature.CLOUD_AUTO_BACKUP,
                tw.local.memonote.entitlement.FeatureOperation.CREATE), CloudBackupState.enabled(isolated))
            val recovered = CloudBackupState.password(isolated)!!
            try { assertTrue(password.contentEquals(recovered)) }
            finally { recovered.fill('\u0000') }
            val stored = isolated.getSharedPreferences("cloud_backup", 0)
                .getString("password", "") ?: ""
            assertFalse(stored.contains("abcde"))
            assertEquals("test@example.com", CloudBackupState.account(isolated))
        } finally {
            password.fill('\u0000')
            isolated.getSharedPreferences("cloud_backup", 0).edit().clear().commit()
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = isolated.packageName + ".cloud_backup_password"
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }
}