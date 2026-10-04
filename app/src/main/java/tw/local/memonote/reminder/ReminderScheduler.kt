package tw.local.memonote.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import tw.local.memonote.data.NoteStore
import tw.local.memonote.ui.AppLanguage

object ReminderScheduler {
    const val ACTION_FIRE = "tw.local.memonote.reminder.FIRE"
    const val CHANNEL = "note_reminders"
    private const val PREFS = "reminder_alarms"
    private const val KEY = "records"
    private data class Record(val noteId: Long, val id: String, val time: Long) {
        fun persist(): String = noteId.toString() + "|" + id + "|" + time
    }

    private fun parse(raw: String): Record? {
        val parts = raw.split('|')
        if (parts.size != 3) return null
        return Record(parts[0].toLongOrNull() ?: return null, parts[1],
            parts[2].toLongOrNull() ?: return null)
    }

    private fun alarm(context: Context) = context.getSystemService(AlarmManager::class.java)
    fun notificationId(noteId: Long, id: String): Int = (noteId.toString() + ":" + id).hashCode()

    private fun pending(context: Context, record: Record): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            .setData(Uri.parse("memonote://reminder/" + record.noteId + "/" + record.id))
            .putExtra("noteId", record.noteId).putExtra("reminderId", record.id)
        return PendingIntent.getBroadcast(context, notificationId(record.noteId, record.id),
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || alarm(context).canScheduleExactAlarms()

    fun notificationAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            AppLanguage.text(context, "筆記時間提醒"),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = AppLanguage.text(context, "在指定時間顯示筆記中的整行文字")
            enableVibration(true)
        })
    }


    @Synchronized
    fun syncAll(context: Context) {
        cancelLegacy(context)
        ReminderAlarms.sync(context.applicationContext)
    }

    @Synchronized
    private fun cancelLegacy(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull(::parse).forEach {
            alarm(context).cancel(pending(context,it))
        }
        if(prefs.contains(KEY)) check(prefs.edit().remove(KEY).commit())
    }

    @Synchronized
    fun forceReschedule(context: Context) {
        cancelLegacy(context)
        ReminderAlarms.sync(context.applicationContext,true)
    }
    fun safeSync(context: Context) {
        runCatching { syncAll(context) }.onFailure {
            Log.e("LittleNotes", "Reminder scheduling failed", it)
        }
    }
}
