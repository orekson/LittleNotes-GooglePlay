package tw.local.memonote.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.util.UUID

object VaultRepository {
    private const val MAX_SEALED_BYTES = 200 * 1024 * 1024

    private fun decrypt(sealed: String, password: CharArray): String {
        require(sealed.length <= MAX_SEALED_BYTES * 4 / 3 + 8) { "加密筆記過大" }
        val bytes = Base64.decode(sealed, Base64.DEFAULT)
        require(bytes.size <= MAX_SEALED_BYTES) { "加密筆記過大" }
        return PasswordCrypto.decryptBytes(bytes, password, PasswordCrypto.NOTE_MAGIC)
            .toString(Charsets.UTF_8)
    }

    fun lock(context: Context, noteId: Long, password: CharArray) {
        val stored = NoteStore(context).use { it.find(noteId) } ?: error("筆記已不存在")
        require(!stored.isLocked) { "筆記已加密" }
        val history=NoteStore(context).use { it.storedHistory(noteId) }
        val protectedHistory=history.associate { version ->
            version.versionId to if(version.note.isLocked) version.note else {
                val bytes=PortableNotes.pack(context,version.note).toByteArray(Charsets.UTF_8)
                NoteHistory.safe(version.note.copy(sealed=Base64.encodeToString(
                    PasswordCrypto.encryptBytes(bytes,password,PasswordCrypto.NOTE_MAGIC),Base64.NO_WRAP)))
            }
        }
        val payload = PortableNotes.pack(context, stored).toByteArray(Charsets.UTF_8)
        val sealed = Base64.encodeToString(
            PasswordCrypto.encryptBytes(payload, password, PasswordCrypto.NOTE_MAGIC),
            Base64.NO_WRAP
        )
        NoteStore(context).use { it.saveLocked(noteId, sealed, protectedHistory) }
        File(context.cacheDir, "preview.json").delete()
        DraftFiles.clearForNote(context, noteId)
        val remaining = NoteStore(context).use { store ->
            store.referencedMedia()
        }
        (PortableNotes.refs(stored)+history.filterNot { it.note.isLocked }.flatMap { PortableNotes.refs(it.note) })
            .filter { it.startsWith("file:") && it !in remaining }.forEach { ref ->
            val name = ref.removePrefix("file:")
            if (name.matches(Regex("[A-Za-z0-9._-]+")) && !name.contains("..")) {
                File(context.filesDir, name).delete()
            }
        }
    }

    fun open(context: Context, locked: Note, password: CharArray, session: String): Note {
        require(locked.isLocked)
        return PortableNotes.openInMemory(decrypt(locked.sealed, password), locked.id, session)
            .copy(updated = locked.updated)
    }

    fun saveEdited(context: Context, draft: Note, password: CharArray) {
        require(!draft.isLocked && draft.id > 0)
        val current=NoteStore(context).use { it.find(draft.id) } ?: error("筆記已不存在")
        require(current.isLocked) { "加密筆記已不存在" }
        val packed=PortableNotes.pack(context,draft)
        if(PortableNotes.sameContent(decrypt(current.sealed,password),packed)) return
        val payload = packed.toByteArray(Charsets.UTF_8)
        val sealed = Base64.encodeToString(
            PasswordCrypto.encryptBytes(payload, password, PasswordCrypto.NOTE_MAGIC),
            Base64.NO_WRAP
        )
        NoteStore(context).use { it.saveLocked(draft.id, sealed) }
    }

    fun verify(locked: Note, password: CharArray) {
        require(locked.isLocked)
        val root = org.json.JSONObject(decrypt(locked.sealed, password))
        require(root.getInt("version") == 1) { "加密筆記格式不支援" }
    }

    /** Restoring an encrypted older version keeps the current note's encryption state. */
    fun restoreVersion(context: Context,noteId: Long,versionId: Long,password: CharArray) {
        NoteStore(context).use { store ->
            val current=store.find(noteId) ?: error("筆記已不存在")
            val version=store.versions(noteId).firstOrNull { it.versionId==versionId }?.note ?: error("版本已不存在")
            verify(version,password)
            if(current.isLocked) { store.restoreVersion(noteId,versionId); return }
            val staging=File(context.filesDir,".restore-version-"+UUID.randomUUID())
            check(staging.mkdir())
            val moved=mutableListOf<File>()
            try {
                val plain=PortableNotes.restoreToDirectory(decrypt(version.sealed,password),staging).copy(id=noteId)
                staging.listFiles().orEmpty().forEach { file ->
                    val target=File(context.filesDir,file.name)
                    check(file.renameTo(target)) { "無法還原附件" }; moved+=target
                }
                store.restoreVersion(noteId,versionId,plain)
            } catch(e: Exception) { moved.forEach { it.delete() }; throw e }
            finally { staging.listFiles().orEmpty().forEach { it.delete() }; staging.delete() }
        }
    }

    fun removePassword(context: Context, locked: Note, password: CharArray) {
        require(locked.isLocked)
        val staging = File(context.filesDir, ".unlock-" + UUID.randomUUID())
        check(staging.mkdir()) { "無法準備附件" }
        val moved = mutableListOf<File>()
        try {
            val plain = PortableNotes.restoreToDirectory(decrypt(locked.sealed, password), staging)
                .copy(id = locked.id)
            staging.listFiles().orEmpty().forEach { file ->
                val target = File(context.filesDir, file.name)
                check(file.renameTo(target)) { "無法還原附件" }
                moved += target
            }
            NoteStore(context).use { it.removeLock(plain) }
        } catch (e: Exception) {
            moved.forEach { it.delete() }
            throw e
        } finally {
            staging.listFiles().orEmpty().forEach { it.delete() }
            staging.delete()
        }
    }
}
