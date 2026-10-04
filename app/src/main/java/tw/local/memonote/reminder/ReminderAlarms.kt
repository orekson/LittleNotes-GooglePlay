package tw.local.memonote.reminder

import android.app.*
import android.content.*
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import tw.local.memonote.data.Note
import tw.local.memonote.data.NoteStore
import tw.local.memonote.entitlement.*

/** Durable, identity-checked one-shot alarms, including the next occurrence of a repeat. */
object ReminderAlarms {
    const val ACTION_SNOOZE="tw.local.memonote.reminder.SNOOZE"
    data class Record(val noteId: Long,val id: String,val fire: Long,val occurrence: Long,
                      val signature: String,val snooze: Boolean=false) {
        fun json()=JSONObject().put("note",noteId).put("id",id).put("fire",fire)
            .put("occurrence",occurrence).put("signature",signature).put("snooze",snooze)
    }
    data class Delivery(val note: Note,val reminder: NoteReminder,val record: Record)
    private fun prefs(c: Context)=c.getSharedPreferences("reminder_v2",Context.MODE_PRIVATE)
    private fun records(c: Context): List<Record> = runCatching {
        val array=JSONArray(prefs(c).getString("alarms","[]"))
        (0 until array.length()).map { i -> val j=array.getJSONObject(i)
            Record(j.getLong("note"),j.getString("id"),j.getLong("fire"),j.getLong("occurrence"),j.getString("signature"),j.optBoolean("snooze")) }
    }.getOrDefault(emptyList())
    private fun save(c: Context, records: List<Record>) {
        check(prefs(c).edit().putString("alarms",JSONArray().apply { records.forEach { put(it.json()) } }.toString()).commit())
    }
    private fun key(noteId: Long,id: String)="delivered:$noteId:$id"
    private fun pending(c: Context,r: Record): PendingIntent {
        val intent=Intent(c,ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_FIRE)
            .setData(Uri.parse("memonote://reminder/v2/${r.noteId}/${r.id}/${r.snooze}"))
            .putExtra("noteId",r.noteId).putExtra("reminderId",r.id).putExtra("fire",r.fire)
            .putExtra("signature",r.signature).putExtra("snooze",r.snooze)
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun cancel(c: Context,r: Record)=c.getSystemService(AlarmManager::class.java).cancel(pending(c,r))
    private fun schedule(c: Context,r: Record) {
        val manager=c.getSystemService(AlarmManager::class.java)
        val pending=pending(c,r)
        try {
            if(ReminderScheduler.exactAllowed(c)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.fire,pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.fire,pending)
        } catch(_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.fire,pending) }
    }
    fun pro(c: Context)=EntitlementManager.allows(c,PremiumFeature.RECURRING_REMINDERS,FeatureOperation.EXECUTE_EXISTING)
    @Synchronized fun sync(c: Context,force: Boolean=false) {
        val old=records(c)
        val now=System.currentTimeMillis()
        val exact=ReminderScheduler.exactAllowed(c)
        val reset=force || prefs(c).getBoolean("exact",exact)!=exact
        val desired=mutableListOf<Record>()
        val live=mutableSetOf<String>()
        NoteStore(c).use { store -> store.all().forEach { note -> ReminderCodec.all(note).forEach { reminder ->
            val deliveredKey=key(note.id,reminder.id); live+=deliveredKey
            if(!reminder.rule.advanced || pro(c)) {
                val signature=reminder.rule.fingerprint(reminder.timeMillis)
                val last=prefs(c).getString(deliveredKey,"").orEmpty().split('|')
                    .let { if(it.size==2 && it[0]==signature) it[1].toLongOrNull() ?: 0 else 0 }
                // Time / timezone broadcasts must recompute future recurrences.
                val previous=if(force) null else old.firstOrNull { it.noteId==note.id && it.id==reminder.id && !it.snooze &&
                    it.signature==signature && it.occurrence>last && it.fire>now-86_400_000L }
                val next=previous ?: reminder.rule.next(reminder.timeMillis,maxOf(now,last-reminder.rule.leadMinutes*60_000L))
                    ?.let { Record(note.id,reminder.id,it.fireTime,it.time,signature) }
                if(next!=null) desired+=next
                if(pro(c)) desired+=old.filter { it.noteId==note.id && it.id==reminder.id && it.snooze &&
                    it.signature==signature && it.fire>now-86_400_000L && (!force || it.fire>now) }
            }
        } } }
        old.filter { reset || it !in desired }.forEach { cancel(c,it) }
        save(c,desired)
        try { desired.filter { it.fire>now && (reset || it !in old) }.forEach { schedule(c,it) } }
        catch(error: Exception) {
            save(c,if(reset) emptyList() else old.filter { it in desired })
            throw error
        }
        val editor=prefs(c).edit().putBoolean("exact",exact)
        prefs(c).all.keys.filter { it.startsWith("delivered:") && it !in live }.forEach { editor.remove(it) }
        editor.apply()
        ReminderScheduler.channel(c)
    }
    @Synchronized fun consume(c: Context,intent: Intent): Delivery? {
        val old=records(c)
        val record=old.firstOrNull { it.noteId==intent.getLongExtra("noteId",0) &&
            it.id==intent.getStringExtra("reminderId") && it.fire==intent.getLongExtra("fire",0) &&
            it.signature==intent.getStringExtra("signature") && it.snooze==intent.getBooleanExtra("snooze",false) } ?: return null
        if(record.fire>System.currentTimeMillis()+1000) return null
        val note=NoteStore(c).use { it.find(record.noteId) } ?: return null
        val reminder=ReminderCodec.all(note).firstOrNull { it.id==record.id } ?: return null
        if(reminder.rule.fingerprint(reminder.timeMillis)!=record.signature ||
            ((reminder.rule.advanced || record.snooze) && !pro(c))) { sync(c); return null }
        save(c,old-record)
        if(!record.snooze) check(prefs(c).edit().putString(key(note.id,reminder.id),"${record.signature}|${record.occurrence}").commit())
        sync(c)
        return Delivery(note,reminder,record)
    }
    fun snoozeAction(c: Context,d: Delivery,minutes: Int): PendingIntent {
        val intent=Intent(c,ReminderReceiver::class.java).setAction(ACTION_SNOOZE)
            .setData(Uri.parse("memonote://snooze/${d.note.id}/${d.reminder.id}/$minutes"))
            .putExtra("noteId",d.note.id).putExtra("reminderId",d.reminder.id)
            .putExtra("signature",d.record.signature).putExtra("occurrence",d.record.occurrence).putExtra("minutes",minutes)
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    @Synchronized fun snooze(c: Context,intent: Intent) {
        if(!pro(c)) return
        val note=NoteStore(c).use { it.find(intent.getLongExtra("noteId",0)) } ?: return
        val reminder=ReminderCodec.all(note).firstOrNull { it.id==intent.getStringExtra("reminderId") } ?: return
        val signature=reminder.rule.fingerprint(reminder.timeMillis)
        if(signature!=intent.getStringExtra("signature")) return
        val occurrence=intent.getLongExtra("occurrence",0)
        if(prefs(c).getString(key(note.id,reminder.id),"")!="$signature|$occurrence") return
        val minutes=intent.getIntExtra("minutes",10).takeIf { it==10 || it==30 } ?: return
        val next=Record(note.id,reminder.id,System.currentTimeMillis()+minutes*60_000L,occurrence,signature,true)
        val old=records(c); old.filter { it.noteId==note.id && it.id==reminder.id && it.snooze }.forEach { cancel(c,it) }
        save(c,old.filterNot { it.noteId==note.id && it.id==reminder.id && it.snooze }+next)
        schedule(c,next)
        c.getSystemService(NotificationManager::class.java).cancel(ReminderScheduler.notificationId(note.id,reminder.id))
    }
}
