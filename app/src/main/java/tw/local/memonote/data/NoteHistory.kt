package tw.local.memonote.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class NoteVersion(val versionId: Long,val noteId: Long,val savedAt: Long,val note: Note)
data class TrashedNote(val noteId: Long,val deletedAt: Long,val note: Note) {
    val expiresAt get()=deletedAt+NoteHistory.TRASH_RETENTION
}

internal object NoteHistory {
    const val TRASH_RETENTION=30L*24*60*60*1000
    const val MAX_VERSIONS=50
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS note_versions (version_id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER NOT NULL, saved_at INTEGER NOT NULL, snapshot TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS versions_note ON note_versions(note_id, version_id DESC)")
        db.execSQL("CREATE TABLE IF NOT EXISTS note_trash (note_id INTEGER PRIMARY KEY, deleted_at INTEGER NOT NULL, snapshot TEXT NOT NULL)")
    }
    fun safe(note: Note)=if(!note.isLocked) note else note.copy(title="",body="",formatting="{}",background="paper",fade=35,category="")
    fun record(db: SQLiteDatabase,before: Note?,after: Note) {
        if(before==null || before.copy(updated=0)==after.copy(updated=0)) return
        val note=safe(before)
        db.insertOrThrow("note_versions",null,ContentValues().apply {
            put("note_id",note.id); put("saved_at",note.updated); put("snapshot",note.toJson())
        })
        db.execSQL("DELETE FROM note_versions WHERE note_id=? AND version_id NOT IN (SELECT version_id FROM note_versions WHERE note_id=? ORDER BY version_id DESC LIMIT $MAX_VERSIONS)",arrayOf(note.id,note.id))
    }
    fun versions(db: SQLiteDatabase,noteId: Long): List<NoteVersion> =
        db.query("note_versions",null,"note_id=?",arrayOf(noteId.toString()),null,null,"version_id DESC").use { c ->
            buildList { while(c.moveToNext()) add(NoteVersion(c.getLong(0),c.getLong(1),c.getLong(2),Note.fromJson(c.getString(3)))) }
        }
    fun trash(db: SQLiteDatabase): List<TrashedNote> =
        db.query("note_trash",null,null,null,null,null,"deleted_at DESC").use { c ->
            buildList { while(c.moveToNext()) add(TrashedNote(c.getLong(0),c.getLong(1),Note.fromJson(c.getString(2)))) }
        }
    fun purgeExpired(db: SQLiteDatabase,now: Long=System.currentTimeMillis()) {
        db.execSQL("DELETE FROM note_versions WHERE note_id IN (SELECT note_id FROM note_trash WHERE deleted_at<=?) AND note_id NOT IN (SELECT id FROM notes)",arrayOf(now-TRASH_RETENTION))
        db.delete("note_trash","deleted_at<=?",arrayOf((now-TRASH_RETENTION).toString()))
    }
}
