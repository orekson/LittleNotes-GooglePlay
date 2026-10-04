package tw.local.memonote.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import tw.local.memonote.cloud.CloudBackupJob
import tw.local.memonote.reminder.ReminderScheduler
import tw.local.memonote.widget.DateWidgetSchedule
import tw.local.memonote.entitlement.*

class NoteStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "notes.db", null, 3) {
    private val appContext = context.applicationContext
    private fun changed() {
        runCatching { CloudBackupJob.schedule(appContext) }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            ReminderScheduler.safeSync(appContext)
            DateWidgetSchedule.safeRefresh(appContext)
        }
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL, " +
                "formatting TEXT NOT NULL, background TEXT NOT NULL, fade INTEGER NOT NULL, " +
                "updated INTEGER NOT NULL, category TEXT NOT NULL DEFAULT '', sealed TEXT NOT NULL DEFAULT '')"
        )
        NoteHistory.create(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE notes ADD COLUMN sealed TEXT NOT NULL DEFAULT ''")
        }
        if(oldVersion<3) NoteHistory.create(db)
    }

    fun all(): List<Note> = read(null, null)
    fun find(id: Long): Note? = read("id=?", arrayOf(id.toString())).firstOrNull()

    private fun read(where: String?, args: Array<String>?): List<Note> =
        readableDatabase.query("notes", null, where, args, null, null, "updated DESC, id DESC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Note(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            title = c.getString(c.getColumnIndexOrThrow("title")),
                            body = c.getString(c.getColumnIndexOrThrow("body")),
                            formatting = c.getString(c.getColumnIndexOrThrow("formatting")),
                            background = c.getString(c.getColumnIndexOrThrow("background")),
                            fade = c.getInt(c.getColumnIndexOrThrow("fade")),
                            updated = c.getLong(c.getColumnIndexOrThrow("updated")),
                            category = c.getString(c.getColumnIndexOrThrow("category")),
                            sealed = c.getString(c.getColumnIndexOrThrow("sealed"))
                        )
                    )
                }
            }
        }

    private fun values(note: Note, updated: Long): ContentValues = ContentValues().apply {
        val locked = note.isLocked
        put("title", if (locked) "" else note.title)
        put("body", if (locked) "" else note.body)
        put("formatting", if (locked) "{}" else note.formatting)
        put("background", if (locked) "paper" else note.background)
        put("fade", if (locked) 35 else note.fade.coerceIn(0, 100))
        put("updated", updated)
        put("category", if (locked) "" else note.category)
        put("sealed", note.sealed)
    }

    fun save(note: Note): Long {
        if (note.id != 0L && find(note.id)?.isLocked == true && !note.isLocked) {
            error("加密筆記必須以密文儲存")
        }
        val db=writableDatabase
        db.beginTransaction()
        try {
        val before=if(note.id>0) find(note.id) else null
        check(before?.isLocked!=true || note.isLocked) { "加密筆記必須以密文儲存" }
        check(before==null || before.isLocked || !note.isLocked) { "請使用加密流程，確保歷史版本一起加密" }
        if(historyAllowed()) NoteHistory.record(db,before,note)
        val v = values(note, System.currentTimeMillis())
        val savedId = if (note.id == 0L) {
            writableDatabase.insertOrThrow("notes", null, v)
        } else {
            check(writableDatabase.update("notes", v, "id=?", arrayOf(note.id.toString())) == 1) {
                "筆記已不存在"
            }
            note.id
        }
        db.setTransactionSuccessful()
        changed()
        return savedId
        } finally { db.endTransaction() }
    }

    fun saveLocked(noteId: Long, sealed: String, encryptedHistory: Map<Long,Note>? = null) {
        require(sealed.isNotBlank())
        val current = find(noteId) ?: error("筆記已不存在")
        val db=writableDatabase
        db.beginTransaction()
        try {
        if(!current.isLocked) {
            val versions=storedHistory(noteId)
            check(versions.all { encryptedHistory?.get(it.versionId)?.isLocked==true }) { "歷史版本必須一起加密" }
            versions.forEach { version ->
                db.update("note_versions",ContentValues().apply { put("snapshot",NoteHistory.safe(encryptedHistory!!.getValue(version.versionId)).toJson()) },
                    "version_id=?",arrayOf(version.versionId.toString()))
            }
        } else if(historyAllowed()) NoteHistory.record(db,current,current.copy(sealed=sealed))
        check(writableDatabase.update(
            "notes",
            values(current.copy(sealed = sealed), System.currentTimeMillis()),
            "id=?",
            arrayOf(noteId.toString())
        ) == 1)
        db.setTransactionSuccessful()
        changed()
        } finally { db.endTransaction() }
    }

    fun removeLock(note: Note) {
        require(note.id > 0 && !note.isLocked)
        check(find(note.id)?.isLocked == true) { "加密筆記已不存在" }
        val db=writableDatabase
        db.beginTransaction()
        try {
        if(historyAllowed()) NoteHistory.record(db,find(note.id),note)
        check(writableDatabase.update(
            "notes", values(note, System.currentTimeMillis()),
            "id=?", arrayOf(note.id.toString())
        ) == 1)
        db.setTransactionSuccessful()
        changed()
        } finally { db.endTransaction() }
    }

    fun insertImported(notes: List<Note>): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            notes.forEach { db.insertOrThrow("notes", null, values(it, it.updated)) }
            db.setTransactionSuccessful()
            changed()
            return notes.size
        } finally {
            db.endTransaction()
        }
    }

    fun delete(id: Long) {
        val db=writableDatabase
        db.beginTransaction()
        try {
        val note=find(id) ?: return
        NoteHistory.purgeExpired(db)
        if(historyAllowed()) db.insertOrThrow("note_trash",null,ContentValues().apply {
            put("note_id",id); put("deleted_at",System.currentTimeMillis()); put("snapshot",NoteHistory.safe(note).toJson())
        }) else db.delete("note_versions","note_id=?",arrayOf(id.toString()))
        db.delete("notes", "id=?", arrayOf(id.toString()))
        db.setTransactionSuccessful()
        changed()
        } finally { db.endTransaction() }
    }

    private fun historyAllowed()=EntitlementManager.allows(appContext,PremiumFeature.VERSION_HISTORY,FeatureOperation.CREATE)
    internal fun storedHistory(noteId: Long)=NoteHistory.versions(readableDatabase,noteId)
    fun versions(noteId: Long): List<NoteVersion> {
        EntitlementManager.require(appContext,PremiumFeature.VERSION_HISTORY,FeatureOperation.VIEW)
        return storedHistory(noteId)
    }
    fun trash(): List<TrashedNote> {
        EntitlementManager.require(appContext,PremiumFeature.VERSION_HISTORY,FeatureOperation.VIEW)
        NoteHistory.purgeExpired(writableDatabase)
        return NoteHistory.trash(readableDatabase)
    }
    fun restoreVersion(noteId: Long,versionId: Long,unlockedVersion: Note? = null) {
        EntitlementManager.require(appContext,PremiumFeature.VERSION_HISTORY,FeatureOperation.MODIFY)
        val db=writableDatabase; db.beginTransaction()
        try {
            val before=find(noteId) ?: error("筆記已不存在")
            val saved=storedHistory(noteId).firstOrNull { it.versionId==versionId }?.note ?: error("版本已不存在")
            val snapshot=if(saved.isLocked && !before.isLocked) {
                check(unlockedVersion!=null && !unlockedVersion.isLocked) { "請輸入此版本密碼以還原" }
                unlockedVersion.copy(id=noteId)
            } else saved
            check(!before.isLocked || snapshot.isLocked) { "不能以未加密版本取代加密筆記" }
            NoteHistory.record(db,before,snapshot)
            check(db.update("notes",values(snapshot,System.currentTimeMillis()),"id=?",arrayOf(noteId.toString()))==1)
            db.setTransactionSuccessful(); changed()
        } finally { db.endTransaction() }
    }
    fun restoreTrash(noteId: Long): Long {
        EntitlementManager.require(appContext,PremiumFeature.VERSION_HISTORY,FeatureOperation.CREATE)
        val db=writableDatabase; db.beginTransaction()
        try {
            NoteHistory.purgeExpired(db)
            val note=NoteHistory.trash(db).firstOrNull { it.noteId==noteId }?.note ?: error("回收桶筆記已不存在或已過期")
            check(find(noteId)==null) { "筆記已經存在" }
            db.insertOrThrow("notes",null,values(note,System.currentTimeMillis()).apply { put("id",noteId) })
            db.delete("note_trash","note_id=?",arrayOf(noteId.toString()))
            db.setTransactionSuccessful(); changed(); return noteId
        } finally { db.endTransaction() }
    }
    fun purgeTrash(noteId: Long) {
        val db=writableDatabase; db.beginTransaction()
        try {
            if(db.delete("note_trash","note_id=?",arrayOf(noteId.toString()))>0 && find(noteId)==null)
                db.delete("note_versions","note_id=?",arrayOf(noteId.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    internal fun referencedMedia(): Set<String> {
        val notes=all()+NoteHistory.trash(readableDatabase).map { it.note }
        val versions=readableDatabase.rawQuery("SELECT snapshot FROM note_versions",null).use { c ->
            buildList { while(c.moveToNext()) add(Note.fromJson(c.getString(0))) }
        }
        return (notes+versions).filterNot { it.isLocked }.flatMap { PortableNotes.refs(it) }.toSet()
    }

    fun toggleCheck(noteId: Long, checkId: String): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val note = find(noteId) ?: return false
            if (note.isLocked) return false
            val changed = CheckState.toggle(note, checkId) ?: return false
            save(changed)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun saveFromEditor(original: Note, draft: Note): Long {
        tw.local.memonote.entitlement.PremiumEdits.requireAllowed(appContext, original, draft)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val latest = find(draft.id)
            check(latest?.isLocked != true) { "請先解鎖筆記" }
            val id = save(if (latest != null) CheckState.merge(original, draft, latest) else draft)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }
}
