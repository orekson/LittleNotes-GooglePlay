package tw.local.memonote.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import tw.local.memonote.data.NoteStore
import tw.local.memonote.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId

/** Date assignments are per widget; a manual choice wins until the next future date. */
object DateWidgetSchedule {
    private const val PREFS = "widget_dates"
    private const val ACTION = "tw.local.memonote.widget.DATE_SWITCH"
    private const val PREFIX = "date:"
    private const val APPLIED = "applied:"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(widgetId: Int, date: LocalDate) = "$PREFIX$widgetId:$date"

    fun assignments(context: Context, widgetId: Int): Map<LocalDate, Long> =
        prefs(context).all.mapNotNull { (key, value) ->
            val prefix = "$PREFIX$widgetId:"
            if (!key.startsWith(prefix)) return@mapNotNull null
            val date = runCatching { LocalDate.parse(key.removePrefix(prefix)) }.getOrNull()
                ?: return@mapNotNull null
            val noteId = value as? Long ?: return@mapNotNull null
            date to noteId
        }.toMap()

    fun assign(context: Context, widgetId: Int, date: LocalDate, noteId: Long) {
        tw.local.memonote.entitlement.EntitlementManager.require(context,
            tw.local.memonote.entitlement.PremiumFeature.WIDGET_DATE_SCHEDULE,
            tw.local.memonote.entitlement.FeatureOperation.MODIFY)
        val today = LocalDate.now()
        require(!date.isBefore(today.minusYears(1)) && !date.isAfter(today.plusYears(1)))
        require(NoteStore(context).use { it.find(noteId) } != null)
        check(prefs(context).edit().putLong(key(widgetId, date), noteId).commit())
        if (!date.isAfter(today)) {
            NoteWidgetProvider.bind(context, widgetId, noteId)
            prefs(context).edit().putLong("$APPLIED$widgetId", today.toEpochDay()).apply()
            NoteWidgetProvider.update(context, widgetId)
        }
        safeRefresh(context)
    }

    fun remove(context: Context, widgetId: Int, date: LocalDate) {
        prefs(context).edit().remove(key(widgetId, date)).apply()
        safeRefresh(context)
    }

    fun manualBind(context: Context, widgetId: Int, noteId: Long) {
        WidgetAccess.requireCanConfigure(context, widgetId)
        NoteWidgetProvider.bind(context, widgetId, noteId)
        prefs(context).edit().putLong("$APPLIED$widgetId", LocalDate.now().toEpochDay()).apply()
        safeRefresh(context)
    }

    fun forget(context: Context, widgetIds: IntArray) {
        val edit = prefs(context).edit()
        widgetIds.forEach { id ->
            assignments(context, id).keys.forEach { date -> edit.remove(key(id, date)) }
            edit.remove("$APPLIED$id")
        }
        edit.apply()
        safeRefresh(context)
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 9001,
            Intent(context, DateSwitchReceiver::class.java).setAction(ACTION)
                .setData(Uri.parse("memonote://date-switch")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun dueAssignment(choices: Map<LocalDate, Long>, appliedEpochDay: Long,
                      today: LocalDate): Map.Entry<LocalDate, Long>? =
        choices.filterKeys { !it.isAfter(today) && it.toEpochDay() > appliedEpochDay }
            .maxByOrNull { it.key }

    @Synchronized
    fun refresh(context: Context) {
        val app = context.applicationContext
        val today = LocalDate.now()
        val ids = AppWidgetManager.getInstance(app)
            .getAppWidgetIds(ComponentName(app, NoteWidgetProvider::class.java))
        val known = NoteStore(app).use { it.all().map { note -> note.id }.toSet() }
        var next: LocalDate? = null
        for (id in ids) {
            val choices = assignments(app, id).filterValues { it in known }
            val applied = prefs(app).getLong("$APPLIED$id", Long.MIN_VALUE)
            val due = dueAssignment(choices, applied, today)
            if (due != null) {
                NoteWidgetProvider.bind(app, id, due.value)
                prefs(app).edit().putLong("$APPLIED$id", due.key.toEpochDay()).apply()
                NoteWidgetProvider.update(app, id)
            }
            val future = choices.keys.filter { it.isAfter(today) }.minOrNull()
            if (future != null && (next == null || future.isBefore(next))) next = future
        }
        val alarm = app.getSystemService(AlarmManager::class.java)
        val intent = pending(app)
        alarm.cancel(intent)
        if (next != null) {
            val whenMillis = next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            try {
                if (ReminderScheduler.exactAllowed(app))
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, intent)
                else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, intent)
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, intent)
            }
        }
    }

    fun safeRefresh(context: Context) {
        runCatching { refresh(context) }.onFailure {
            Log.e("LittleNotes", "Date widget refresh failed", it)
        }
    }

    fun isDateSwitch(intent: Intent) = intent.action == ACTION
}

class DateSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DateWidgetSchedule.isDateSwitch(intent)) DateWidgetSchedule.safeRefresh(context)
    }
}
