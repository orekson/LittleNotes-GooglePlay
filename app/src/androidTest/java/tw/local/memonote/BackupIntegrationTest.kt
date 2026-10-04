package tw.local.memonote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tw.local.memonote.data.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun archiveRestoresCategoriesAttachmentsAndLockedNotesWithoutOverwriting() {
        val marker = UUID.randomUUID().toString()
        val imageName = "img_" + UUID.randomUUID() + ".png"
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
        val image = File(context.filesDir, imageName)
        image.writeBytes(imageBytes)
        val formatting = JSONObject().put("stickers", JSONArray().put(
            JSONObject().put("at", 0).put("ref", "file:$imageName")
        )).toString()
        val plainId = NoteStore(context).use {
            it.save(Note(title = "搬家測試", body = "\uFFFC", formatting = formatting, category = marker))
        }
        val lockedId = NoteStore(context).use {
            it.save(Note(title = "秘密標題", body = "秘密內容", category = marker + "-locked"))
        }
        val notePassword = "note-secret-123".toCharArray()
        val backupPassword = "backup-secret-123".toCharArray()
        val originalIds = NoteStore(context).use { store -> store.all().map { it.id }.toSet() }
        try {
            VaultRepository.lock(context, lockedId, notePassword)
            val lockedOnDisk = NoteStore(context).use { it.find(lockedId)!! }
            assertEquals("", lockedOnDisk.title)
            assertEquals("", lockedOnDisk.body)
            assertEquals("", lockedOnDisk.category)
            val output = ByteArrayOutputStream()
            val savedCount = BackupRepository.write(context, output, backupPassword)
            assertEquals(originalIds.size, savedCount)
            assertThrows(Exception::class.java) {
                BackupRepository.restore(context, ByteArrayInputStream(output.toByteArray()),
                    "wrong-password".toCharArray())
            }
            assertEquals(originalIds.size, NoteStore(context).use { it.all().size })

            val imported = BackupRepository.restore(
                context, ByteArrayInputStream(output.toByteArray()), backupPassword
            )
            assertEquals(savedCount, imported)
            val all = NoteStore(context).use { it.all() }
            assertTrue(all.any { it.id == plainId })
            assertTrue(all.any { it.id == lockedId })
            val restoredPlain = all.single { it.category == marker && it.id != plainId }
            val restoredRef = JSONObject(restoredPlain.formatting)
                .getJSONArray("stickers").getJSONObject(0).getString("ref")
            assertArrayEquals(imageBytes, ImageFiles.readBytes(context, restoredRef))
            val restoredLocked = all.single { it.id != lockedId && it.sealed == lockedOnDisk.sealed }
            val opened = VaultRepository.open(context, restoredLocked, notePassword, UUID.randomUUID().toString())
            assertEquals("秘密標題", opened.title)
            assertEquals("秘密內容", opened.body)
            assertEquals(marker + "-locked", opened.category)
        } finally {
            val after = NoteStore(context).use { it.all() }
            after.filter { it.id !in originalIds }.forEach { imported ->
                PortableNotes.refs(imported).filter { it.startsWith("file:") }.forEach {
                    File(context.filesDir, it.removePrefix("file:")).delete()
                }
                NoteStore(context).use { it.delete(imported.id) }
            }
            NoteStore(context).use { it.delete(plainId); it.delete(lockedId) }
            image.delete()
            notePassword.fill('\u0000')
            backupPassword.fill('\u0000')
        }
    }
}