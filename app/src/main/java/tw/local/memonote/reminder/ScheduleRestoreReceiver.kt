package tw.local.memonote.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import tw.local.memonote.widget.DateWidgetSchedule

class ScheduleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val result = goAsync()
        Thread {
            try {
                ReminderScheduler.forceReschedule(context.applicationContext)
                DateWidgetSchedule.safeRefresh(context.applicationContext)
            } catch (error: Exception) {
                Log.e("LittleNotes", "Could not restore schedules", error)
            } finally {
                result.finish()
            }
        }.start()
    }
}