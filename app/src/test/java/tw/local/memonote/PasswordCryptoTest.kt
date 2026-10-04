package tw.local.memonote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import tw.local.memonote.data.PasswordCrypto

class PasswordCryptoTest {
    private val password = "correct horse battery staple".toCharArray()

    @Test fun backupRoundTripAcrossSeveralChunks() {
        val clear = ByteArray(150_000) { (it * 31).toByte() }
        val encrypted = PasswordCrypto.encryptBytes(clear, password, PasswordCrypto.BACKUP_MAGIC)
        assertArrayEquals(clear, PasswordCrypto.decryptBytes(encrypted, password, PasswordCrypto.BACKUP_MAGIC))
    }

    @Test fun wrongPasswordCannotReadBackup() {
        val encrypted = PasswordCrypto.encryptBytes("notes".toByteArray(), password, PasswordCrypto.BACKUP_MAGIC)
        assertThrows(Exception::class.java) {
            PasswordCrypto.decryptBytes(encrypted, "incorrect".toCharArray(), PasswordCrypto.BACKUP_MAGIC)
        }
    }

    @Test fun modifiedOrTruncatedBackupIsRejected() {
        val encrypted = PasswordCrypto.encryptBytes("notes".toByteArray(), password, PasswordCrypto.BACKUP_MAGIC)
        val modified = encrypted.clone().also { it[it.lastIndex - 20] = (it[it.lastIndex - 20].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) {
            PasswordCrypto.decryptBytes(modified, password, PasswordCrypto.BACKUP_MAGIC)
        }
        assertThrows(Exception::class.java) {
            PasswordCrypto.decryptBytes(encrypted.copyOf(encrypted.size - 10), password, PasswordCrypto.BACKUP_MAGIC)
        }
    }

    @Test fun noteAndBackupFormatsAreSeparated() {
        val encrypted = PasswordCrypto.encryptBytes("secret".toByteArray(), password, PasswordCrypto.NOTE_MAGIC)
        assertThrows(Exception::class.java) {
            PasswordCrypto.decryptBytes(encrypted, password, PasswordCrypto.BACKUP_MAGIC)
        }
    }
}
