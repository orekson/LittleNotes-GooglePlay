package tw.local.memonote.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NoteStore(context: Context): SQLiteOpenHelper(context.applicationContext,"notes.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL, formatting TEXT NOT NULL, background TEXT NOT NULL, fade INTEGER NOT NULL, updated INTEGER NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun all(): List<Note> = read(null,null)
    fun find(id: Long): Note? = read("id=?",arrayOf(id.toString())).firstOrNull()
    private fun read(where: String?,args: Array<String>?): List<Note> = readableDatabase.query("notes",null,where,args,null,null,"updated DESC, id DESC").use { c ->
        buildList { while(c.moveToNext()) add(Note(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5),c.getLong(6))) }
    }
    fun save(note: Note): Long {
        val v=ContentValues().apply { put("title",note.title); put("body",note.body); put("formatting",note.formatting); put("background",note.background); put("fade",note.fade.coerceIn(0,100)); put("updated",System.currentTimeMillis()) }
        if(note.id==0L) return writableDatabase.insertOrThrow("notes",null,v)
        check(writableDatabase.update("notes",v,"id=?",arrayOf(note.id.toString()))==1) { "筆記已不存在" }
        return note.id
    }
    fun delete(id: Long) { writableDatabase.delete("notes","id=?",arrayOf(id.toString())) }
    fun toggleCheck(noteId: Long,checkId: String): Boolean {
        val db=writableDatabase; db.beginTransaction()
        try {
            val note=find(noteId) ?: return false
            val changed=CheckState.toggle(note,checkId) ?: return false
            save(changed); db.setTransactionSuccessful(); return true
        } finally { db.endTransaction() }
    }
    fun saveFromEditor(original: Note,draft: Note): Long {
        val db=writableDatabase; db.beginTransaction()
        try {
            val latest=find(draft.id)
            val id=save(if(latest!=null) CheckState.merge(original,draft,latest) else draft)
            db.setTransactionSuccessful(); return id
        } finally { db.endTransaction() }
    }
}
