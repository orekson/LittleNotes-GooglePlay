package tw.local.memonote.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The .lnbackup archive is password encrypted before it reaches a document provider. */
object BackupRepository {
    private const val VERSION = 1
    private const val MAX_NOTES = 5000
    private const val MAX_ENTRY_BYTES = 300L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

    fun write(context: Context, output: OutputStream, password: CharArray): Int {
        val notes = NoteStore(context).use { it.all() }
        require(notes.size <= MAX_NOTES) { "筆記數量超過備份上限" }
        ZipOutputStream(PasswordCrypto.encrypting(output, password, PasswordCrypto.BACKUP_MAGIC)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(JSONObject().put("version", VERSION).put("count", notes.size)
                .toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            notes.forEachIndexed { index, note ->
                val json = if (note.isLocked) {
                    JSONObject().put("kind", "locked").put("note", JSONObject(note.toJson()))
                } else {
                    JSONObject().put("kind", "plain")
                        .put("portable", JSONObject(PortableNotes.pack(context, note)))
                }
                zip.putNextEntry(ZipEntry("notes/" + (index + 1).toString().padStart(8, '0') + ".json"))
                zip.write(json.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return notes.size
    }

    fun restore(context: Context, input: InputStream, password: CharArray): Int {
        val staging = File(context.filesDir, ".restore-" + UUID.randomUUID())
        val moved = mutableListOf<File>()
        try {
            check(staging.mkdir()) { "無法建立暫存目錄" }
            val notes = PasswordCrypto.decrypting(input, password, PasswordCrypto.BACKUP_MAGIC).use { clear ->
                val limited = object : InputStream() {
                    var total = 0L
                    private fun count(amount: Int) {
                        total += amount
                        if (total > MAX_TOTAL_BYTES) throw IOException("備份檔超過 512 MB")
                    }
                    override fun read(): Int = clear.read().also { if (it >= 0) count(1) }
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                        clear.read(bytes, offset, length).also { if (it > 0) count(it) }
                    override fun close() = clear.close()
                }
                ZipInputStream(limited).use { zip ->
                    val parsed = readArchive(zip, staging)
                    // ZIP ends before the encrypted stream's authenticated final record.
                    val buffer = ByteArray(32 * 1024)
                    while (limited.read(buffer) >= 0) { }
                    parsed
                }
            }
            staging.listFiles().orEmpty().forEach { file ->
                val target = File(context.filesDir, file.name)
                check(!target.exists() && file.renameTo(target)) { "無法匯入附件" }
                moved += target
            }
            NoteStore(context).use { it.insertImported(notes) }
            return notes.size
        } catch (e: Exception) {
            moved.forEach { it.delete() }
            throw e
        } finally {
            staging.listFiles().orEmpty().forEach { it.delete() }
            staging.delete()
        }
    }

    private fun readArchive(zip: ZipInputStream, staging: File): List<Note> {
        val notes = mutableListOf<Note>()
        val seen = mutableSetOf<Int>()
        var declaredCount: Int? = null
        var totalRead = 0L
        run {
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "備份檔含有非預期目錄" }
                val limit = if (entry.name == "manifest.json") 1024L * 1024 else MAX_ENTRY_BYTES
                val data = ByteArrayOutputStream()
                val buffer = ByteArray(32 * 1024)
                var entryRead = 0L
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    entryRead += count
                    totalRead += count
                    require(entryRead <= limit && totalRead <= MAX_TOTAL_BYTES) { "備份內容超過大小上限" }
                    data.write(buffer, 0, count)
                }
                val raw = data.toByteArray().toString(Charsets.UTF_8)
                if (entry.name == "manifest.json") {
                    require(declaredCount == null) { "重複的備份目錄" }
                    val manifest = JSONObject(raw)
                    require(manifest.getInt("version") == VERSION) { "備份版本不支援" }
                    declaredCount = manifest.getInt("count").also {
                        require(it in 0..MAX_NOTES) { "筆記數量不正確" }
                    }
                } else {
                    val name = Regex("notes/([0-9]{8})[.]json").matchEntire(entry.name)
                        ?: error("備份檔含有非預期項目")
                    val index = name.groupValues[1].toInt()
                    require(index in 1..MAX_NOTES && seen.add(index)) { "筆記項目重複或無效" }
                    val item = JSONObject(raw)
                    val note = when (item.getString("kind")) {
                        "plain" -> PortableNotes.restoreToDirectory(
                            item.getJSONObject("portable").toString(), staging
                        )
                        "locked" -> {
                            val locked = Note.fromJson(item.getJSONObject("note").toString())
                            require(locked.isLocked && locked.sealed.length <= MAX_ENTRY_BYTES)
                            val header = Base64.decode(locked.sealed, Base64.DEFAULT)
                            require(header.size >= 33 &&
                                header.copyOfRange(0, 8).contentEquals(
                                    PasswordCrypto.NOTE_MAGIC.toByteArray(Charsets.US_ASCII)
                                )
                            ) { "加密筆記格式不正確" }
                            locked.copy(id = 0)
                        }
                        else -> error("備份筆記類型不支援")
                    }
                    notes += note
                }
                zip.closeEntry()
            }
        }
        require(declaredCount == notes.size && seen.size == notes.size &&
            (1..notes.size).all { it in seen }) { "備份筆記數量不完整" }
        return notes
    }
}
