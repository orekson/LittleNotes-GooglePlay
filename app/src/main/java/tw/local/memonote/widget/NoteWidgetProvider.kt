package tw.local.memonote.widget

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import tw.local.memonote.*
import tw.local.memonote.data.NoteStore
import tw.local.memonote.rich.NoteRenderer

class NoteWidgetProvider: AppWidgetProvider() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action==ACTION_TOGGLE) {
            val widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
            val note=intent.getLongExtra("noteId",0); val check=intent.getStringExtra("checkId") ?: return
            val ids=AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,NoteWidgetProvider::class.java))
            if(widgetId !in ids || note<=0 || noteId(context,widgetId)!=note) return
            try { if(NoteStore(context).use { it.toggleCheck(note,check) }) updateAll(context) }
            catch(e: Exception) { Log.e("MemoNote","Checkbox update failed",e) }
            return
        }
        super.onReceive(context,intent)
    }
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) { ids.forEach { update(context,it) } }
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) { update(context,id) }
    override fun onDeleted(context: Context,ids: IntArray) { val prefs=context.getSharedPreferences("widgets",Context.MODE_PRIVATE).edit(); ids.forEach { prefs.remove(it.toString()) }; prefs.apply() }
    companion object {
        const val ACTION_TOGGLE="tw.local.memonote.TOGGLE_CHECK"
        fun noteId(context: Context,widgetId: Int)=context.getSharedPreferences("widgets",Context.MODE_PRIVATE).getLong(widgetId.toString(),0)
        fun bind(context: Context,widgetId: Int,noteId: Long) { check(context.getSharedPreferences("widgets",Context.MODE_PRIVATE).edit().putLong(widgetId.toString(),noteId).commit()) { "小工具設定儲存失敗" } }
        fun activeWidth(options: Bundle,landscape: Boolean): Int {
            val min=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300).coerceAtLeast(1)
            return options.getInt(if(landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,min).coerceIn(100,800)
        }
        fun widthDp(context: Context,id: Int): Int = activeWidth(AppWidgetManager.getInstance(context).getAppWidgetOptions(id),context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        fun updateAll(context: Context) { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,NoteWidgetProvider::class.java)).forEach { update(context,it) } }
        fun update(context: Context,id: Int) {
            try {
                val manager=AppWidgetManager.getInstance(context)
                val note=NoteStore(context).use { it.find(noteId(context,id)) }
                val views=RemoteViews(context.packageName,R.layout.note_widget)
                views.setTextViewText(R.id.widget_title,note?.displayTitle ?: "小小筆記")
                val configure=Intent(context,WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                views.setOnClickPendingIntent(R.id.widget_choose,PendingIntent.getActivity(context,id*2,configure,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.setOnClickPendingIntent(R.id.widget_message,PendingIntent.getActivity(context,id*2,configure,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                val edit=Intent(context,EditorActivity::class.java).putExtra("noteId",note?.id ?: 0)
                views.setOnClickPendingIntent(R.id.widget_edit,PendingIntent.getActivity(context,id*2+1,edit,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.setViewVisibility(R.id.widget_edit,if(note==null) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_list,if(note==null) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_message,if(note==null) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_message,if(noteId(context,id)==0L) "選一篇筆記，放在這裡陪你。\n點一下開始選擇" else "這篇筆記已刪除。\n點一下重新選擇")
                val width=widthDp(context,id); val height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,400).coerceIn(100,1000)
                val bw=width.coerceAtMost(360); val bh=(height.toFloat()/width*bw).toInt().coerceIn(100,800)
                views.setImageViewBitmap(R.id.widget_background,NoteRenderer.background(context,note?.background ?: "paper",note?.fade ?: 35,bw,bh))
                views.setRemoteAdapter(R.id.widget_list,Intent(context,NoteWidgetService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("memonote://widget/$id")))
                val toggle=Intent(context,NoteWidgetProvider::class.java).setAction(ACTION_TOGGLE).setData(Uri.parse("memonote://toggle/$id")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                val mutable=if(android.os.Build.VERSION.SDK_INT>=31) PendingIntent.FLAG_MUTABLE else 0
                views.setPendingIntentTemplate(R.id.widget_list,PendingIntent.getBroadcast(context,id,toggle,PendingIntent.FLAG_UPDATE_CURRENT or mutable))
                manager.updateAppWidget(id,views)
                manager.notifyAppWidgetViewDataChanged(id,R.id.widget_list)
            } catch(e: Exception) { Log.e("MemoNote","Widget $id update failed",e) }
        }
    }
}
