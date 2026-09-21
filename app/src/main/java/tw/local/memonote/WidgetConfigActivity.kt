package tw.local.memonote

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.*
import tw.local.memonote.data.NoteStore
import tw.local.memonote.ui.Ui
import tw.local.memonote.widget.NoteWidgetProvider

class WidgetConfigActivity: Activity() {
    private var widgetId=AppWidgetManager.INVALID_APPWIDGET_ID
    override fun onCreate(state: Bundle?) { super.onCreate(state); widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID); setResult(RESULT_CANCELED); if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID) finish() }
    override fun onResume() { super.onResume(); if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID) return; showChoices() }
    private fun showChoices() {
        val root=Ui.root(this); val scroll=ScrollView(this); val content=Ui.column(this); Ui.pad(content,24); scroll.addView(content); root.addView(scroll)
        content.addView(Ui.label(this,"放一篇筆記在桌面",26f,bold=true)); content.addView(Ui.space(this,10)); content.addView(Ui.label(this,"每個小工具都能選不同的筆記。",15f,Ui.muted)); content.addView(Ui.space(this,20))
        val notes=try { NoteStore(this).use { it.all() } } catch(e: Exception) { Ui.toast(this,"筆記讀取失敗，請重試"); emptyList() }
        notes.forEach { note -> content.addView(Ui.button(this,note.displayTitle) {
            try { NoteWidgetProvider.bind(this,widgetId,note.id); NoteWidgetProvider.update(this,widgetId); setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId)); finish() }
            catch(e: Exception) { Ui.toast(this,"設定未能儲存，請重試") }
        }) }
        if(notes.isEmpty()) content.addView(Ui.label(this,"還沒有筆記，先寫一篇吧。",16f))
        content.addView(Ui.space(this,16)); content.addView(Ui.button(this,"＋ 建立新筆記",true) { startActivity(Intent(this,EditorActivity::class.java)) }); content.addView(Ui.button(this,"取消") { finish() })
    }
}
