package tw.local.memonote.reminder

import android.app.*
import android.widget.*
import tw.local.memonote.R
import tw.local.memonote.entitlement.PremiumFeature
import tw.local.memonote.rich.ReminderSpan
import tw.local.memonote.ui.*
import java.time.*
import java.time.format.DateTimeFormatter

object ReminderDialog {
    fun show(activity: Activity, existing: ReminderSpan?, message: String,
             save: (Long,ReminderRule)->Unit, remove: ()->Unit) {
        var time=existing?.timeMillis ?: System.currentTimeMillis()+3_600_000L
        var rule=existing?.rule ?: ReminderRule()
        val zone=ZoneId.systemDefault()
        val content=Ui.column(activity).apply { Ui.pad(this,16) }
        content.addView(Ui.label(activity,"Free · 單次日期與時間提醒",17f,Ui.purple,true))
        content.addView(Ui.rawLabel(activity,"提醒內容："+message.ifBlank { "請先在筆記中輸入事項或標題" },14f))
        val date=Ui.button(activity,"") {}
        val clock=Ui.button(activity,"") {}
        val repeating=Ui.button(activity,"") {}
        val interval=Ui.button(activity,"") {}
        val lead=Ui.button(activity,"") {}
        val end=Ui.button(activity,"") {}
        val next=Ui.label(activity,"",13f,Ui.muted)
        val labels=mapOf(Repeat.NONE to "單次（Free）",Repeat.DAILY to "每天",Repeat.WEEKDAYS to "週一至週五",
            Repeat.WEEKLY to "每週",Repeat.MONTHLY to "每月")
        fun refresh() {
            val at=Instant.ofEpochMilli(time).atZone(zone)
            date.text="日期：${at.toLocalDate()}"; clock.text="時間：${at.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            repeating.text="重複：${labels[rule.repeat]}"
            interval.text="重複間隔：${rule.interval} ${when(rule.repeat) { Repeat.WEEKLY->"週"; Repeat.MONTHLY->"月"; else->"天" }}"
            interval.visibility=if(rule.repeat in listOf(Repeat.DAILY,Repeat.WEEKLY,Repeat.MONTHLY)) android.view.View.VISIBLE else android.view.View.GONE
            lead.text="提前通知：${rule.leadMinutes} 分鐘"; end.text="結束日期：${rule.until.ifEmpty { "不限制" }}"
            end.visibility=if(rule.repeat==Repeat.NONE) android.view.View.GONE else android.view.View.VISIBLE
            val upcoming=rule.next(time,System.currentTimeMillis())
            next.text=if(rule.advanced && !ReminderAlarms.pro(activity)) "目前為 Free，這個進階提醒暫停。可轉為單次提醒或刪除。"
                else upcoming?.let { "下次通知："+Instant.ofEpochMilli(it.fireTime).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) }
                ?: "提醒時間已過，請調整日期／時間。"
        }
        date.setOnClickListener {
            val current=Instant.ofEpochMilli(time).atZone(zone)
            DatePickerDialog(activity,{ _,y,m,d -> time=LocalDate.of(y,m+1,d).atTime(current.toLocalTime()).atZone(zone).toInstant().toEpochMilli(); refresh() },
                current.year,current.monthValue-1,current.dayOfMonth).show()
        }
        clock.setOnClickListener {
            val current=Instant.ofEpochMilli(time).atZone(zone)
            TimePickerDialog(activity,R.style.DigitalTimePickerDialog,{ _,h,m ->
                time=current.toLocalDate().atTime(h,m).atZone(zone).toInstant().toEpochMilli(); refresh()
            },current.hour,current.minute,true).show()
        }
        content.addView(date); content.addView(clock)
        content.addView(Ui.space(activity,12))
        content.addView(Ui.label(activity,"Pro · 重複與進階提醒",17f,Ui.purple,true))
        content.addView(Ui.label(activity,"重複、提前通知、結束日期；通知可稍後 10 或 30 分鐘再提醒。",13f,Ui.muted))
        repeating.setOnClickListener {
            AlertDialog.Builder(activity).setTitle("重複方式")
                .setSingleChoiceItems(Repeat.entries.map { labels.getValue(it)+(if(it==Repeat.NONE) "" else " · Pro") }.toTypedArray(),rule.repeat.ordinal) { dialog,index ->
                    if(index==0 || ProUi.require(activity,PremiumFeature.RECURRING_REMINDERS)) {
                        rule=rule.copy(repeat=Repeat.entries[index]).normalized(); refresh(); dialog.dismiss()
                    }
                }.setNegativeButton("取消",null).show()
        }
        interval.setOnClickListener {
            if(ProUi.require(activity,PremiumFeature.RECURRING_REMINDERS)) {
                val input=EditText(activity).apply { inputType=2; setText(rule.interval.toString()); selectAll() }
                val dialog=AlertDialog.Builder(activity).setTitle("重複間隔（1–365）").setView(input)
                    .setNegativeButton("取消",null).setPositiveButton("套用",null).create()
                dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value=input.text.toString().toIntOrNull()
                    if(value==null || value !in 1..365) input.error="請輸入 1–365"
                    else { rule=rule.copy(interval=value); refresh(); dialog.dismiss() }
                } }; dialog.show()
            }
        }
        lead.setOnClickListener {
            val values=listOf(0,5,10,30,60,1440)
            AlertDialog.Builder(activity).setTitle("提前通知")
                .setItems(values.map { if(it==0) "準時通知（Free）" else "$it 分鐘前 · Pro" }.toTypedArray()) { _,index ->
                    if(index==0 || ProUi.require(activity,PremiumFeature.RECURRING_REMINDERS)) { rule=rule.copy(leadMinutes=values[index]); refresh() }
                }.show()
        }
        end.setOnClickListener {
            if(ProUi.require(activity,PremiumFeature.RECURRING_REMINDERS)) {
                val current=runCatching { LocalDate.parse(rule.until) }.getOrDefault(LocalDate.now().plusMonths(1))
                val picker=DatePickerDialog(activity,{ _,y,m,d -> rule=rule.copy(until=LocalDate.of(y,m+1,d).toString()); refresh() },current.year,current.monthValue-1,current.dayOfMonth)
                picker.setButton(AlertDialog.BUTTON_NEUTRAL,"不限制") { _,_ -> rule=rule.copy(until=""); refresh() }; picker.show()
            }
        }
        content.addView(repeating); content.addView(interval); content.addView(lead); content.addView(end)
        content.addView(Ui.button(activity,"轉成 Free 單次提醒") { rule=ReminderRule(); refresh() })
        content.addView(next)
        content.addView(Ui.label(activity,"儲存筆記後才會啟用變更。重複提醒依本機時區；每月月底會使用該月最後一天。",12f,Ui.muted))
        val builder=AlertDialog.Builder(activity).setTitle(if(existing==null) "新增時間提醒" else "編輯時間提醒")
            .setView(ScrollView(activity).apply { addView(content) }).setNegativeButton("取消",null).setPositiveButton("套用",null)
        if(existing!=null) builder.setNeutralButton("刪除提醒") { _,_ -> remove() }
        val dialog=builder.create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            rule=rule.copy(zone=zone.id).normalized()
            when {
                message.isBlank() -> Ui.toast(activity,"請先在提醒所在行輸入事項，或填寫筆記標題")
                rule.advanced && !ProUi.require(activity,PremiumFeature.RECURRING_REMINDERS) -> Unit
                rule.next(time,System.currentTimeMillis())==null -> Ui.toast(activity,"請選擇未來的通知時間，並確認結束日期")
                else -> { save(time,rule); dialog.dismiss() }
            }
        } }
        refresh(); dialog.show()
    }
}
