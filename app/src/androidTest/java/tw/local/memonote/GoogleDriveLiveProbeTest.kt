package tw.local.memonote

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import tw.local.memonote.cloud.CloudAuth
import tw.local.memonote.cloud.CloudBackupState
import tw.local.memonote.cloud.CloudDriveClient
import tw.local.memonote.data.PasswordCrypto
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Explicit opt-in diagnostic against the account already authorized by the user. */
class GoogleDriveLiveProbeTest {
    @Test fun authorizedAccountCanListAppDriveFiles() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realCloudProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = CloudAuth.token(context)
        val request = URL("https://www.googleapis.com/drive/v3/files?pageSize=1&fields=files(id)")
            .openConnection() as HttpURLConnection
        try {
            request.connectTimeout = 30_000
            request.readTimeout = 30_000
            request.setRequestProperty("Authorization", "Bearer $token")
            val status = request.responseCode
            // Only an error response is reported; tokens and successful file data stay private.
            val reason = if (status == 200) "" else request.errorStream?.bufferedReader()?.use {
                it.readText().take(8192)
            }.orEmpty()
            assertEquals("Drive rejected the actual authorized request: $reason", 200, status)
        } finally {
            request.disconnect()
        }
    }

    @Test fun latestAuthorizedDriveBackupCanBeDownloadedAndDecrypted() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realCloudProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val token = CloudAuth.token(targetContext)
        val client = CloudDriveClient(token)
        val backup = client.backups(targetContext.packageName).firstOrNull()
            ?: throw AssertionError("No LittleNotes backup exists in Drive")
        val password = CloudBackupState.password(targetContext)
            ?: throw AssertionError("The app has no saved backup password")
        try {
            val noteCount = client.readBackup(backup.id) { encrypted ->
                val clear = PasswordCrypto.decrypting(encrypted, password, PasswordCrypto.BACKUP_MAGIC)
                clear.use { decrypted ->
                    val zip = ZipInputStream(decrypted)
                    val buffer = ByteArray(32 * 1024)
                    var manifestCount: Int? = null
                    var notes = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name == "manifest.json") {
                            val manifest = ByteArrayOutputStream()
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                manifest.write(buffer, 0, read)
                                require(manifest.size() <= 1024 * 1024) { "Backup manifest is too large" }
                            }
                            manifestCount = JSONObject(manifest.toByteArray().toString(Charsets.UTF_8))
                                .getInt("count")
                        } else if (entry.name.matches(Regex("notes/[0-9]{8}[.]json"))) {
                            notes++
                            while (zip.read(buffer) >= 0) { }
                        } else {
                            throw AssertionError("Unexpected file in encrypted backup")
                        }
                        zip.closeEntry()
                    }
                    // The ZIP central directory ends before the authenticated encryption trailer.
                    while (decrypted.read(buffer) >= 0) { }
                    assertEquals("The manifest should match the encrypted note entries", manifestCount, notes)
                    manifestCount ?: throw AssertionError("Backup manifest is missing")
                }
            }
            assertTrue("The real encrypted Drive backup should contain notes", noteCount > 0)
        } finally {
            password.fill('\u0000')
        }
    }
}
