package tw.local.memonote.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import tw.local.memonote.EditorActivity
import tw.local.memonote.R
import tw.local.memonote.data.NoteStore
import tw.local.memonote.ui.AppLanguage
import tw.local.memonote.ui.localizedDisplayTitle

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action==ReminderAlarms.ACTION_SNOOZE) {
            runCatching { ReminderAlarms.snooze(context,intent) }
                .onFailure { Log.e("LittleNotes","Snooze failed",it) }
            return
        }
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val noteId = intent.getLongExtra("noteId", 0)
        val id = intent.getStringExtra("reminderId") ?: return
        if (noteId <= 0) return
        try {
            val delivery=ReminderAlarms.consume(context,intent) ?: return
            val note=delivery.note
            val reminder=delivery.reminder
            if (!ReminderScheduler.notificationAllowed(context)) return
            val line = ReminderCodec.line(note, reminder)
            if (line.isBlank()) return
            ReminderScheduler.channel(context)
            val open = Intent(context, EditorActivity::class.java)
                .putExtra("noteId", noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(Uri.parse("memonote://open-reminder/$id"))
            val click = PendingIntent.getActivity(context, ReminderScheduler.notificationId(noteId, id), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = Notification.Builder(context, ReminderScheduler.CHANNEL)
                .setSmallIcon(R.drawable.notification_reminder)
                .setContentTitle(note.localizedDisplayTitle(context))
                .setContentText(line)
                .setStyle(Notification.BigTextStyle().bigText(line))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(click)
            if(ReminderAlarms.pro(context)) {
                for(minutes in listOf(10,30)) builder.addAction(Notification.Action.Builder(null,
                    "${minutes} 分鐘後提醒",ReminderAlarms.snoozeAction(context,delivery,minutes)).build())
            }
            val notification=builder.build()
            context.getSystemService(NotificationManager::class.java)
                .notify(ReminderScheduler.notificationId(noteId, id), notification)
        } catch (error: Exception) {
            Log.e("LittleNotes", "Reminder notification failed", error)
        }
    }
}
