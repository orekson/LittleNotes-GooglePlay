package tw.local.memonote

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import tw.local.memonote.ui.AppLanguage
import tw.local.memonote.ui.ProUi
import tw.local.memonote.entitlement.PremiumFeature
import tw.local.memonote.entitlement.ProRequiredException
import tw.local.memonote.model.NoteColors
import tw.local.memonote.widget.WidgetAccess
import tw.local.memonote.widget.WidgetAppearance
import tw.local.memonote.widget.WidgetStyle
import tw.local.memonote.widget.WidgetStyleDialog
import tw.local.memonote.ui.LocalizedActivity
import android.os.Bundle
import android.widget.*
import tw.local.memonote.data.NoteStore
import tw.local.memonote.ui.Ui
import tw.local.memonote.ui.localizedDisplayTitle
import tw.local.memonote.widget.NoteWidgetProvider
import tw.local.memonote.widget.DateWidgetSchedule

class WidgetConfigActivity: LocalizedActivity() {
    private var widgetId=AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedNoteId=0L
    private var selectedAppearance=WidgetAppearance.FOLLOW_NOTE
    private var selectedStyle=WidgetStyle()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId))
        selectedNoteId=state?.getLong("selectedNoteId") ?: NoteWidgetProvider.noteId(this,widgetId)
        selectedAppearance=state?.getString("selectedAppearance") ?: WidgetAppearance.selected(this,widgetId)
        selectedStyle=state?.getString("selectedStyle")?.let { WidgetStyle.parse(it) } ?: WidgetStyle.stored(this,widgetId)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("selectedNoteId",selectedNoteId)
        outState.putString("selectedAppearance",selectedAppearance)
        outState.putString("selectedStyle",selectedStyle.json())
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); if(widgetId!=AppWidgetManager.INVALID_APPWIDGET_ID) showChoices() }
    private fun showChoices() {
        val root=Ui.root(this); val scroll=ScrollView(this); val content=Ui.column(this); Ui.pad(content,24); scroll.addView(content); root.addView(scroll)
        if(!WidgetAccess.canConfigure(this,widgetId)) {
            content.addView(Ui.label(this,"桌面筆記數量已達免費版上限",24f,bold=true))
            content.addView(Ui.space(this,12))
            content.addView(Ui.label(this,"免費版最多可建立 2 個桌面筆記。\n升級 Pro 可建立不限數量的 Widget。",16f))
            content.addView(Ui.space(this,16))
            content.addView(Ui.button(this,"查看 Pro",true) { ProUi.openPage(this) })
            content.addView(Ui.button(this,"取消") { finish() })
            return
        }
        content.addView(Ui.label(this,"放一篇筆記在桌面",26f,bold=true)); content.addView(Ui.space(this,10)); content.addView(Ui.label(this,"每個小工具都能選不同的筆記。",15f,Ui.muted)); content.addView(Ui.space(this,20))
        val notes=try { NoteStore(this).use { it.all() } } catch(e: Exception) { Ui.toast(this,"筆記讀取失敗，請重試"); emptyList() }
        notes.forEach { note -> content.addView(Ui.rawButton(this,
            (if(note.id==selectedNoteId) "✓  " else "")+note.localizedDisplayTitle(this)) {
            selectedNoteId=note.id; showChoices()
        }) }
        if(notes.isEmpty()) content.addView(Ui.label(this,"還沒有筆記，先寫一篇吧。",16f))
        content.addView(Ui.space(this,16))
        content.addView(Ui.label(this,"Widget 背景",18f,bold=true))
        content.addView(Ui.label(this,"可跟隨筆記或選擇免費預設背景。",13f,Ui.muted))
        content.addView(Ui.button(this,appearanceLabel()) { chooseAppearance() })
        content.addView(ProUi.button(this,"圓角、間距、字體與圖片",PremiumFeature.ADVANCED_WIDGET_CUSTOMIZATION) {
            WidgetStyleDialog.show(this,selectedStyle,notes.firstOrNull { it.id==selectedNoteId }) {
                selectedStyle=it; showChoices()
            }
        })
        content.addView(Ui.label(this,"Pro：圓角、內距、行距、字型、字級、透明度與圖片顯示。",13f,Ui.muted))
        content.addView(Ui.button(this,"重設進階外觀") { selectedStyle=WidgetStyle(); showChoices() })
        content.addView(Ui.space(this,16))
        content.addView(Ui.button(this,"完成設定",true) { complete() })
        content.addView(Ui.button(this,"＋ 建立新筆記") { startActivity(Intent(this,EditorActivity::class.java)) })
        content.addView(Ui.button(this,"取消") { finish() })
    }

    private fun appearanceLabel(): String = when(selectedAppearance) {
        WidgetAppearance.FOLLOW_NOTE -> "背景：跟隨筆記"
        "paper" -> "背景：奶油"
        "sakura" -> "背景：櫻花"
        "ocean" -> "背景：海風"
        "night" -> "背景：星夜"
        else -> "背景：自訂顏色"
    }

    private fun chooseAppearance() {
        val labels=listOf("跟隨筆記","奶油","櫻花","海風","星夜",
            ProUi.label(this,"自訂背景顏色",PremiumFeature.WIDGET_BACKGROUND_COLOR))
        val values=listOf(WidgetAppearance.FOLLOW_NOTE,"paper","sakura","ocean","night")
        AlertDialog.Builder(this).setTitle(AppLanguage.text(this,"Widget 背景"))
            .setItems(labels.map { AppLanguage.text(this,it) }.toTypedArray()) { _,index ->
                if(index<values.size) { selectedAppearance=values[index]; showChoices() }
                else if(ProUi.require(this,PremiumFeature.WIDGET_BACKGROUND_COLOR)) chooseColor()
            }.show()
    }

    private fun chooseColor() {
        val colors=NoteColors.palette
        AlertDialog.Builder(this).setTitle(AppLanguage.text(this,"自訂背景顏色"))
            .setItems((colors.map { AppLanguage.text(this,it.first) } +
                AppLanguage.text(this,"輸入色碼（#RRGGBB）")).toTypedArray()) { _,index ->
                if(index<colors.size) {
                    selectedAppearance=NoteColors.backgroundRef(colors[index].second); showChoices()
                } else enterColor()
            }.show()
    }

    private fun enterColor() {
        val input=EditText(this).apply {
            isSingleLine=true
            filters=arrayOf(android.text.InputFilter.LengthFilter(7))
            setText("#FFFFFF")
            selectAll()
        }
        AlertDialog.Builder(this).setTitle(AppLanguage.text(this,"輸入色碼（#RRGGBB）"))
            .setView(input)
            .setNegativeButton(AppLanguage.text(this,"取消"),null)
            .setPositiveButton(AppLanguage.text(this,"套用")) { _,_ ->
                val value=input.text.toString().trim()
                if(!value.matches(Regex("#[0-9a-fA-F]{6}"))) {
                    Ui.toast(this,"請輸入有效的 #RRGGBB 色碼")
                } else {
                    selectedAppearance=NoteColors.backgroundRef(Color.parseColor(value))
                    showChoices()
                }
            }.show()
    }

    private fun complete() {
        if(selectedNoteId==0L) { Ui.toast(this,"請先選擇一篇筆記"); return }
        try {
            WidgetAccess.requireCanConfigure(this,widgetId)
            WidgetAppearance.requireAllowed(this,widgetId,selectedAppearance)
            WidgetStyle.set(this,widgetId,selectedStyle)
            DateWidgetSchedule.manualBind(this,widgetId,selectedNoteId)
            WidgetAppearance.set(this,widgetId,selectedAppearance)
            NoteWidgetProvider.update(this,widgetId)
            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId))
            finish()
        } catch(e: ProRequiredException) { ProUi.show(this,e.feature) }
        catch(e: Exception) { Ui.toast(this,"設定未能儲存，請重試") }
    }
}
