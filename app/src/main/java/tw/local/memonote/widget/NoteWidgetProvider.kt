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
import tw.local.memonote.ui.AppLanguage
import tw.local.memonote.ui.localizedDisplayTitle

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
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) { DateWidgetSchedule.safeRefresh(context); ids.forEach { update(context,it) } }
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) { update(context,id) }
    override fun onDeleted(context: Context,ids: IntArray) { val prefs=context.getSharedPreferences("widgets",Context.MODE_PRIVATE).edit(); ids.forEach { prefs.remove(it.toString()) }; prefs.apply(); WidgetAppearance.forget(context,ids); WidgetStyle.forget(context,ids); DateWidgetSchedule.forget(context,ids) }
    companion object {
        const val ACTION_TOGGLE="tw.local.memonote.TOGGLE_CHECK"
        fun noteId(context: Context,widgetId: Int)=context.getSharedPreferences("widgets",Context.MODE_PRIVATE).getLong(widgetId.toString(),0)
        fun bind(context: Context,widgetId: Int,noteId: Long) { check(context.getSharedPreferences("widgets",Context.MODE_PRIVATE).edit().putLong(widgetId.toString(),noteId).commit()) { "小工具設定儲存失敗" } }
        fun activeWidth(options: Bundle,landscape: Boolean): Int {
            val min=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300).coerceAtLeast(1)
            return options.getInt(if(landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,min).coerceIn(100,800)
        }
        fun widthDp(context: Context,id: Int): Int = activeWidth(AppWidgetManager.getInstance(context).getAppWidgetOptions(id),context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        fun updateAll(context: Context) {
            try {
                AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,NoteWidgetProvider::class.java)).forEach { update(context,it) }
            } catch(e: Exception) { Log.e("MemoNote","Widget refresh failed",e) }
        }
        fun update(context: Context,id: Int) {
            try {
                val manager=AppWidgetManager.getInstance(context)
                val note=NoteStore(context).use { it.find(noteId(context,id)) }
                val views=RemoteViews(context.packageName,R.layout.note_widget)
                views.setTextViewText(R.id.widget_title,note?.localizedDisplayTitle(context) ?: AppLanguage.wrap(context).getString(R.string.app_name))
                val configure=Intent(context,WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                views.setOnClickPendingIntent(R.id.widget_choose,PendingIntent.getActivity(context,id*2,configure,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                val messageTarget=if(note?.isLocked==true) Intent(context,EditorActivity::class.java).putExtra("noteId",note.id) else configure
                views.setOnClickPendingIntent(R.id.widget_message,PendingIntent.getActivity(context,id*2+3,messageTarget,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                val edit=Intent(context,EditorActivity::class.java).putExtra("noteId",note?.id ?: 0)
                views.setOnClickPendingIntent(R.id.widget_edit,PendingIntent.getActivity(context,id*2+1,edit,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.setViewVisibility(R.id.widget_edit,if(note==null || note.isLocked) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_list,if(note==null || note.isLocked) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_message,if(note==null || note.isLocked) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_message,AppLanguage.text(context,if(note?.isLocked==true) "🔒 加密筆記\n點一下輸入密碼" else if(noteId(context,id)==0L) "選一篇筆記，放在這裡陪你。\n點一下開始選擇" else "這篇筆記已刪除。\n點一下重新選擇"))
                val width=widthDp(context,id); val height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,400).coerceIn(100,1000)
                val bw=width.coerceAtMost(360); val bh=(height.toFloat()/width*bw).toInt().coerceIn(100,800)
                views.setTextViewText(R.id.widget_edit,AppLanguage.text(context,"編輯"))
                views.setTextViewText(R.id.widget_choose,AppLanguage.text(context,"換筆記"))
                val widgetBackground=WidgetAppearance.background(context,id,note?.background ?: "paper")
                val style=WidgetStyle.effective(context,id)
                val shape=when(style.corner) {
                    0 -> R.drawable.widget_corner_0
                    12 -> R.drawable.widget_corner_12
                    36 -> R.drawable.widget_corner_36
                    else -> R.drawable.widget_corner_24
                }
                views.setInt(R.id.widget_root,"setBackgroundResource",shape)
                views.setInt(R.id.widget_background,"setImageAlpha",style.opacity*255/100)
                views.setInt(R.id.widget_header,"setBackgroundColor",
                    Color.argb(style.opacity*204/100,255,251,246))
                val inset=(style.padding*context.resources.displayMetrics.density).toInt()
                views.setViewPadding(R.id.widget_header,inset,0,6,0)
                views.setTextViewTextSize(R.id.widget_title,android.util.TypedValue.COMPLEX_UNIT_SP,style.fontSize+2f)
                views.setImageViewBitmap(R.id.widget_background,NoteRenderer.background(context,
                    widgetBackground,note?.fade ?: 35,bw,bh))
                views.setTextColor(R.id.widget_message,WidgetAppearance.textColor(widgetBackground))
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
