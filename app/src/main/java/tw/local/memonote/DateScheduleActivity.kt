package tw.local.memonote

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tw.local.memonote.data.Note
import tw.local.memonote.data.NoteStore
import tw.local.memonote.ui.AppLanguage
import tw.local.memonote.ui.BottomNavigation
import tw.local.memonote.ui.LocalizedActivity
import tw.local.memonote.ui.Ui
import tw.local.memonote.ui.ProUi
import tw.local.memonote.entitlement.*
import tw.local.memonote.ui.localizedDisplayTitle
import tw.local.memonote.widget.DateCalendarModel
import tw.local.memonote.widget.DateWidgetSchedule
import tw.local.memonote.widget.NoteWidgetProvider
import tw.local.memonote.widget.WidgetAccess
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

class DateScheduleActivity : LocalizedActivity() {
    private var selectedDate = LocalDate.now()
    private var visibleMonth = YearMonth.from(selectedDate)
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onResume() {
        super.onResume()
        showPage()
    }

    private fun activeWidgets(): IntArray = AppWidgetManager.getInstance(this)
        .getAppWidgetIds(ComponentName(this, NoteWidgetProvider::class.java))

    private fun label(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd", AppLanguage.locale(this)))

    private fun firstAllowedDate(): LocalDate = LocalDate.now().minusYears(1)
    private fun lastAllowedDate(): LocalDate = LocalDate.now().plusYears(1)

    private fun showPage() {
        val ids = activeWidgets()
        if (widgetId !in ids) widgetId = ids.firstOrNull() ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val notes = runCatching { NoteStore(this).use { it.all() } }.getOrDefault(emptyList())
        val root = Ui.root(this)
        val scroll = ScrollView(this)
        val content = Ui.column(this)
        Ui.pad(content, 24)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(Ui.rawLabel(this,ProUi.label(this,"日期",PremiumFeature.WIDGET_DATE_SCHEDULE),30f,bold=true))
        ProUi.addFeatureHint(this,content,"Pro 可新增與修改日期排程；既有安排會繼續執行，也可取消。")
        content.addView(Ui.space(this, 8))
        content.addView(Ui.label(this,
            AppLanguage.format(this, "今天：%1\$s", label(LocalDate.now())),
            15f, Ui.muted))
        content.addView(Ui.label(this, "可選擇今天前後一年內的日期，指定已寫好的筆記。", 13f, Ui.muted))
        content.addView(Ui.space(this, 16))
        if (ids.isEmpty()) {
            content.addView(Ui.label(this, "先加入一個桌面小工具，再設定日期切換。", 16f))
            content.addView(Ui.button(this, "加入桌面小工具", true) {
                if (!WidgetAccess.canRequestNew(this)) {
                    ProUi.show(this, PremiumFeature.UNLIMITED_WIDGETS)
                    return@button
                }
                val manager = AppWidgetManager.getInstance(this)
                if (manager.isRequestPinAppWidgetSupported)
                    manager.requestPinAppWidget(ComponentName(this, NoteWidgetProvider::class.java),
                        null, null)
                else Ui.toast(this, "請長按桌面空白處，從小工具清單加入小小筆記。")
            })
            content.addView(buildCalendar(notes, emptyMap()))
            content.addView(Ui.space(this, 12))
            content.addView(Ui.label(this,
                AppLanguage.format(this, "所選日期：%1\$s", label(selectedDate)), 16f))
        } else {
            val index = ids.indexOf(widgetId)
            val current = notes.firstOrNull { it.id == NoteWidgetProvider.noteId(this, widgetId) }
            content.addView(Ui.button(this,
                AppLanguage.format(this, "桌面小工具 %1\$d：%2\$s", index + 1,
                    current?.localizedDisplayTitle(this) ?: AppLanguage.text(this, "尚未選擇筆記"))) {
                val labels = ids.mapIndexed { i, id ->
                    val title = notes.firstOrNull { it.id == NoteWidgetProvider.noteId(this, id) }
                        ?.localizedDisplayTitle(this) ?: AppLanguage.text(this, "尚未選擇筆記")
                    AppLanguage.format(this, "桌面小工具 %1\$d：%2\$s", i + 1, title)
                }.toTypedArray()
                AlertDialog.Builder(this).setTitle(AppLanguage.text(this, "選擇桌面小工具"))
                    .setItems(labels) { _, which -> widgetId = ids[which]; showPage() }
                    .show()
            })
            content.addView(Ui.space(this, 12))
            val assignments = DateWidgetSchedule.assignments(this, widgetId)
            val assignmentTitles = assignments.mapValues { (_, noteId) ->
                notes.firstOrNull { it.id == noteId }?.localizedDisplayTitle(this)
            }
            content.addView(buildCalendar(notes, assignmentTitles))
            content.addView(Ui.space(this, 12))
            val assigned = assignments[selectedDate]
            val note = notes.firstOrNull { it.id == assigned }
            content.addView(Ui.label(this,
                AppLanguage.format(this, "所選日期：%1\$s", label(selectedDate)), 16f, bold = true))
            content.addView(Ui.rawLabel(this,
                if (note == null) AppLanguage.text(this, "這一天尚未指定筆記")
                else AppLanguage.format(this, "已指定：%1\$s", note.localizedDisplayTitle(this)),
                14f, Ui.muted))
            content.addView(ProUi.button(this,"選擇這一天的筆記",PremiumFeature.WIDGET_DATE_SCHEDULE) { chooseNote(notes) })
            if (assigned != null) content.addView(Ui.button(this, "移除這一天的安排") {
                DateWidgetSchedule.remove(this, widgetId, selectedDate)
                showPage()
            })
        }
        BottomNavigation.add(this, root, BottomNavigation.Tab.DATE)
    }

    private fun buildCalendar(
        notes: List<Note>,
        assignments: Map<LocalDate, String?>,
    ): LinearLayout {
        val calendar = Ui.column(this)
        val locale = AppLanguage.locale(this)
        val minimum = firstAllowedDate()
        val maximum = lastAllowedDate()
        val firstMonth = YearMonth.from(minimum)
        val lastMonth = YearMonth.from(maximum)
        if (visibleMonth.isBefore(firstMonth)) visibleMonth = firstMonth
        if (visibleMonth.isAfter(lastMonth)) visibleMonth = lastMonth

        val monthBar = Ui.row(this)
        val previous = Ui.button(this, "‹") {
            if (visibleMonth.isAfter(firstMonth)) {
                visibleMonth = visibleMonth.minusMonths(1)
                showPage()
            }
        }.apply { isEnabled = visibleMonth.isAfter(firstMonth) }
        monthBar.addView(previous, LinearLayout.LayoutParams(Ui.dp(this, 56), -2))
        val monthTitle = Ui.label(this,
            visibleMonth.format(DateTimeFormatter.ofPattern("yyyy MMMM", locale)),
            16f, bold = true)
        monthTitle.gravity = Gravity.CENTER
        monthBar.addView(monthTitle, LinearLayout.LayoutParams(0, -2, 1f))
        val next = Ui.button(this, "›") {
            if (visibleMonth.isBefore(lastMonth)) {
                visibleMonth = visibleMonth.plusMonths(1)
                showPage()
            }
        }.apply { isEnabled = visibleMonth.isBefore(lastMonth) }
        monthBar.addView(next, LinearLayout.LayoutParams(Ui.dp(this, 56), -2))
        calendar.addView(monthBar)

        val firstDay = WeekFields.of(locale).firstDayOfWeek
        val weekdays = Ui.row(this)
        repeat(7) { offset ->
            val weekday = firstDay.plus(offset.toLong())
            val weekdayLabel = TextView(this).apply {
                text = weekday.getDisplayName(TextStyle.SHORT, locale)
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Ui.muted)
            }
            weekdays.addView(weekdayLabel, LinearLayout.LayoutParams(0, Ui.dp(this, 24), 1f))
        }
        calendar.addView(weekdays)

        val cells = DateCalendarModel.monthCells(
            visibleMonth, firstDay, assignments, minimum, maximum)
        cells.chunked(7).forEach { week ->
            val row = Ui.row(this)
            week.forEach { cell ->
                val date = cell.date
                val cellView = Ui.column(this).apply {
                    gravity = Gravity.CENTER
                    val isCurrentMonth = date?.let { YearMonth.from(it) == visibleMonth } == true
                    val drawable = GradientDrawable().apply {
                        cornerRadius = Ui.dp(this@DateScheduleActivity, 5).toFloat()
                        setColor(if (cell.isAssigned) 0xffdef1ff.toInt() else Color.TRANSPARENT)
                        if (date == selectedDate)
                            setStroke(Ui.dp(this@DateScheduleActivity, 1), 0xff6caee8.toInt())
                    }
                    background = drawable
                    isClickable = date != null
                    isFocusable = date != null
                    if (date != null) {
                        contentDescription = if (cell.isAssigned && !cell.noteTitle.isNullOrBlank())
                            label(date) + "，" + cell.noteTitle
                        else label(date)
                        setOnClickListener {
                            selectedDate = date
                            visibleMonth = YearMonth.from(date)
                            showPage()
                            if (widgetId in activeWidgets()) chooseNote(notes)
                            else Ui.toast(this@DateScheduleActivity,
                                "先加入一個桌面小工具，再設定日期切換。")
                        }
                    }
                    val dayNumber = TextView(this@DateScheduleActivity).apply {
                        text = date?.dayOfMonth?.toString().orEmpty()
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTextColor(if (isCurrentMonth) Ui.ink else Ui.muted)
                    }
                    addView(dayNumber, LinearLayout.LayoutParams(-1, Ui.dp(this@DateScheduleActivity, 20)))
                    val noteTitle = TextView(this@DateScheduleActivity).apply {
                        text = cell.noteTitle.orEmpty()
                        textSize = 8f
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        setTextColor(Ui.ink)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        includeFontPadding = false
                        visibility = if (cell.isAssigned && !cell.noteTitle.isNullOrBlank())
                            View.VISIBLE else View.INVISIBLE
                    }
                    addView(noteTitle, LinearLayout.LayoutParams(-1, 0, 1f))
                }
                val spacing = Ui.dp(this, 1)
                row.addView(cellView, LinearLayout.LayoutParams(0, Ui.dp(this, 62), 1f).apply {
                    setMargins(spacing, spacing, spacing, spacing)
                })
            }
            calendar.addView(row)
        }
        return calendar
    }

    private fun chooseNote(notes: List<Note>) {
        if (notes.isEmpty()) {
            Ui.toast(this, "還沒有筆記，先寫一篇吧。")
            return
        }
        val labels = notes.map { it.localizedDisplayTitle(this) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(AppLanguage.format(this, "選擇 %1\$s 要顯示的筆記", label(selectedDate)))
            .setItems(labels) { _, index ->
                if (!ProUi.require(this,PremiumFeature.WIDGET_DATE_SCHEDULE,FeatureOperation.MODIFY)) return@setItems
                runCatching {
                    DateWidgetSchedule.assign(this, widgetId, selectedDate, notes[index].id)
                }.onSuccess {
                    Ui.toast(this, "日期安排已儲存")
                    showPage()
                }.onFailure { Ui.toast(this, "日期安排儲存失敗，請重試") }
            }
            .setNegativeButton(AppLanguage.text(this, "取消"), null)
            .show()
    }
}
