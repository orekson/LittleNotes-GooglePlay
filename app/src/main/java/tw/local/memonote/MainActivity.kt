package tw.local.memonote

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.Bundle
import android.widget.*
import tw.local.memonote.data.*
import tw.local.memonote.ui.Ui
import tw.local.memonote.widget.NoteWidgetProvider
import java.text.SimpleDateFormat
import java.util.*

class MainActivity: Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() { super.onResume(); showNotes() }
    private fun showNotes() {
        val root=Ui.root(this)
        val header=Ui.column(this); Ui.pad(header,24)
        header.addView(Ui.label(this,"✦  MEMO / MY LITTLE SPACE",11f,Ui.purple,true))
        header.addView(Ui.space(this,8)); header.addView(Ui.label(this,"小小筆記",32f,bold=true))
        header.addView(Ui.space(this,6)); header.addView(Ui.label(this,"把日常寫下，讓喜歡的陪在桌面。",14f,Ui.muted))
        header.addView(Ui.space(this,12))
        header.addView(Ui.button(this,"＋  寫一篇新筆記",true) { startActivity(Intent(this,EditorActivity::class.java)) })
        root.addView(header)
        val scroll=ScrollView(this); val list=Ui.column(this); list.setPadding(Ui.dp(this,24),0,Ui.dp(this,24),Ui.dp(this,24)); scroll.addView(list)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val notes=try { NoteStore(this).use { it.all() } } catch(e: Exception) { Ui.toast(this,"筆記讀取失敗，請重新開啟重試"); emptyList() }
        list.addView(Ui.label(this,"我的筆記  ·  ${notes.size}",13f,Ui.muted,true)); list.addView(Ui.space(this,12))
        if(notes.isEmpty()) {
            val empty=Ui.column(this); Ui.pad(empty,24); empty.background=Ui.rounded(0xfff0e9fa.toInt(),28f)
            empty.addView(Ui.label(this,"✧",40f,Ui.purple)); empty.addView(Ui.label(this,"這一頁，留給你",21f,bold=true)); empty.addView(Ui.space(this,8))
            empty.addView(Ui.label(this,"寫下今天的小事，插入喜歡的小人貼圖，再放到桌面隨時看見。",15f,Ui.muted)); list.addView(empty)
        }
        val date=SimpleDateFormat("MM/dd  HH:mm",Locale.TAIWAN)
        notes.forEach { note ->
            val card=Ui.column(this); Ui.pad(card,20); card.background=Ui.rounded(0xffffffff.toInt(),24f)
            card.elevation=Ui.dp(this,1).toFloat()
            card.addView(Ui.label(this,note.displayTitle,20f,bold=true))
            card.addView(Ui.space(this,7)); card.addView(Ui.label(this,note.body.replace("\uFFFC"," ✿ ").take(120).ifBlank { "只有標題，也是一篇筆記。" },15f,Ui.muted).apply { maxLines=3 })
            card.addView(Ui.space(this,12)); card.addView(Ui.label(this,"${date.format(Date(note.updated))}   ·   點一下繼續編輯",11f,Ui.purple))
            card.setOnClickListener { startActivity(Intent(this,EditorActivity::class.java).putExtra("noteId",note.id)) }
            list.addView(card); list.addView(Ui.space(this,12))
        }
        list.addView(Ui.space(this,12)); list.addView(Ui.button(this,"✧  加到桌面小工具") { addWidget() })
        list.addView(Ui.button(this,"貼圖來源與使用說明") {
            AlertDialog.Builder(this).setTitle(getString(R.string.sticker_source_title)).setMessage(getString(R.string.sticker_source_message))
                .setPositiveButton("知道了",null).setNeutralButton(getString(R.string.sticker_source_action)) { _,_->
                    startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(getString(R.string.sticker_source_url))))
                }.show()
        })
    }
    private fun addWidget() {
        val manager=AppWidgetManager.getInstance(this)
        if(manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(this,NoteWidgetProvider::class.java),null,null)
        else AlertDialog.Builder(this).setTitle("加入桌面").setMessage("長按桌面空白處 → 小工具 → 小小筆記。放到空白桌面頁，再長按調整大小。").setPositiveButton("知道了",null).show()
    }
}
