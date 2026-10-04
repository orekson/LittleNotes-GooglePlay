package tw.local.memonote.reminder

import org.json.JSONObject
import java.time.*
import java.time.temporal.ChronoUnit

enum class Repeat { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY }
data class ReminderOccurrence(val time: Long, val fireTime: Long)
data class ReminderRule(
    val repeat: Repeat = Repeat.NONE, val interval: Int = 1, val leadMinutes: Int = 0,
    val until: String = "", val zone: String = ZoneId.systemDefault().id
) {
    val advanced get() = repeat != Repeat.NONE || leadMinutes > 0 || until.isNotEmpty() || interval != 1
    fun normalized() = copy(interval=if(repeat==Repeat.NONE || repeat==Repeat.WEEKDAYS) 1 else interval.coerceIn(1,365),
        leadMinutes=leadMinutes.coerceIn(0,1440),
        until=if(repeat==Repeat.NONE) "" else runCatching { LocalDate.parse(until).toString() }.getOrDefault(""),
        zone=runCatching { ZoneId.of(zone).id }.getOrDefault(ZoneId.systemDefault().id))
    fun write(json: JSONObject): JSONObject = json.put("repeat",repeat.name).put("interval",interval)
        .put("leadMinutes",leadMinutes).put("until",until).put("zone",zone)
    fun fingerprint(start: Long): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest("$start|${repeat.name}|$interval|$leadMinutes|$until|$zone".toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** Recurrences follow local wall time; month-end and DST gaps use java.time resolution. */
    fun next(start: Long, after: Long, localZone: ZoneId = ZoneId.systemDefault()): ReminderOccurrence? {
        val rule=normalized()
        if(rule!=this) return rule.next(start,after,localZone)
        val lead=leadMinutes*60_000L
        if(repeat==Repeat.NONE) return ReminderOccurrence(start,start-lead).takeIf { it.fireTime>after }
        val anchor=Instant.ofEpochMilli(start).atZone(ZoneId.of(zone))
        val first=anchor.toLocalDate()
        val time=anchor.toLocalTime()
        val search=Instant.ofEpochMilli(after+lead).atZone(localZone).toLocalDate()
        val end=until.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        fun result(date: LocalDate): ReminderOccurrence? {
            if(date<first || (end!=null && date>end)) return null
            val millis=date.atTime(time).atZone(localZone).toInstant().toEpochMilli()
            return ReminderOccurrence(millis,millis-lead).takeIf { it.fireTime>after }
        }
        if(repeat==Repeat.WEEKDAYS) {
            val from=maxOf(first,search)
            for(offset in 0L..7L) {
                val day=from.plusDays(offset)
                if(day.dayOfWeek.value<=5) result(day)?.let { return it }
            }
            return null
        }
        val units=when(repeat) {
            Repeat.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(first),YearMonth.from(search))
            Repeat.WEEKLY -> ChronoUnit.DAYS.between(first,search)/7
            else -> ChronoUnit.DAYS.between(first,search)
        }
        val initial=(units/interval-1).coerceAtLeast(0)
        for(index in initial..initial+4) {
            val date=when(repeat) {
                Repeat.MONTHLY -> first.plusMonths(index*interval)
                Repeat.WEEKLY -> first.plusWeeks(index*interval)
                else -> first.plusDays(index*interval)
            }
            result(date)?.let { return it }
        }
        return null
    }
    companion object {
        fun read(json: JSONObject) = ReminderRule(
            runCatching { Repeat.valueOf(json.optString("repeat","NONE")) }.getOrDefault(Repeat.NONE),
            json.optInt("interval",1),json.optInt("leadMinutes",0),json.optString("until",""),
            json.optString("zone",ZoneId.systemDefault().id)).normalized()
    }
}
