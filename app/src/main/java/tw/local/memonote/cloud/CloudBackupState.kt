package tw.local.memonote.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only the password's Keystore-encrypted ciphertext is stored on the device. */
object CloudBackupState {
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val PREFS = "cloud_backup"
    private const val SECRET = "password"
    private const val ALIAS_SUFFIX = ".cloud_backup_password"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun connected(context: Context) = prefs(context).getBoolean("connected", false)
    fun requested(context: Context) = prefs(context).getBoolean("enabled", false)
    fun enabled(context: Context) = requested(context) &&
        tw.local.memonote.entitlement.EntitlementManager.allows(context,
            tw.local.memonote.entitlement.PremiumFeature.CLOUD_AUTO_BACKUP,
            tw.local.memonote.entitlement.FeatureOperation.EXECUTE_EXISTING)
    fun account(context: Context): String = prefs(context).getString("account", "") ?: ""
    fun lastSuccess(context: Context): Long = prefs(context).getLong("last_success", 0L)
    fun problem(context: Context): String = prefs(context).getString("problem", "") ?: ""

    fun connect(context: Context, account: String, password: CharArray) {
        require(password.size >= 5) { "備份密碼至少需要 5 個字元" }
        savePassword(context, password)
        check(prefs(context).edit().putString("account", account)
            .putBoolean("connected", true).putBoolean("enabled",
                tw.local.memonote.entitlement.EntitlementManager.allows(context,
                    tw.local.memonote.entitlement.PremiumFeature.CLOUD_AUTO_BACKUP,
                    tw.local.memonote.entitlement.FeatureOperation.CREATE))
            .remove("problem").commit()) { "無法儲存雲端備份設定" }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled) tw.local.memonote.entitlement.EntitlementManager.require(context,
            tw.local.memonote.entitlement.PremiumFeature.CLOUD_AUTO_BACKUP,
            tw.local.memonote.entitlement.FeatureOperation.CREATE)
        check(prefs(context).edit().putBoolean("enabled", enabled)
            .remove("problem").commit()) { "無法儲存雲端備份設定" }
    }

    fun markSuccess(context: Context, at: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong("last_success", at).remove("problem").apply()
    }

    fun markProblem(context: Context, description: String) {
        prefs(context).edit().putString("problem", description).apply()
    }

    fun disconnect(context: Context) {
        CloudBackupJob.cancel(context)
        val store = keyStore()
        val alias = context.packageName + ALIAS_SUFFIX
        if (store.containsAlias(alias)) store.deleteEntry(alias)
        check(prefs(context).edit().clear().commit()) { "無法清除雲端備份設定" }
    }

    fun password(context: Context): CharArray? {
        val raw = prefs(context).getString(SECRET, null) ?: return null
        val packed = Base64.decode(raw, Base64.NO_WRAP)
        require(packed.size > 12) { "備份密碼資料損壞" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(context), GCMParameterSpec(128, packed, 0, 12))
        val clear = cipher.doFinal(packed, 12, packed.size - 12)
        return try {
            val buffer = Charsets.UTF_8.decode(ByteBuffer.wrap(clear))
            CharArray(buffer.remaining()).also { buffer.get(it) }
        } finally {
            clear.fill(0)
            packed.fill(0)
        }
    }

    private fun savePassword(context: Context, password: CharArray) {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val clear = ByteArray(buffer.remaining()).also { buffer.get(it) }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(context))
            val encrypted = cipher.doFinal(clear)
            val packed = cipher.iv + encrypted
            check(prefs(context).edit().putString(SECRET, Base64.encodeToString(packed, Base64.NO_WRAP))
                .commit()) { "無法儲存雲端備份密碼" }
            encrypted.fill(0)
            packed.fill(0)
        } finally {
            clear.fill(0)
        }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(context: Context): SecretKey {
        val alias = context.packageName + ALIAS_SUFFIX
        val existing = keyStore().getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}