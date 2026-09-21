package tw.local.memonote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.*
import tw.local.memonote.R
import tw.local.memonote.data.NoteStore
import tw.local.memonote.rich.NoteRenderer

class NoteWidgetService: RemoteViewsService() {
    override fun onConfigurationChanged(config: android.content.res.Configuration) { super.onConfigurationChanged(config); NoteWidgetProvider.updateAll(this) }
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID))
    inner class Factory(private val widgetId: Int): RemoteViewsFactory {
        @Volatile private var renderer: NoteRenderer?=null
        override fun onCreate()=reload()
        override fun onDataSetChanged()=reload()
        private fun reload() {
            renderer=try {
                val note=NoteStore(this@NoteWidgetService).use { it.find(NoteWidgetProvider.noteId(this@NoteWidgetService,widgetId)) }
                note?.let {
                    val dp=NoteWidgetProvider.widthDp(this@NoteWidgetService,widgetId)
                    val width=(dp*1.5f).toInt().coerceAtMost(540)
                    NoteRenderer(this@NoteWidgetService,it,width,width.toFloat()/dp)
                }
            } catch(e: Exception) { null }
        }
        override fun onDestroy() { renderer=null }
        override fun getCount()=renderer?.tiles?.size ?: 0
        override fun getViewAt(position: Int): RemoteViews? {
            val current=renderer ?: return null
            if(position !in current.tiles.indices) return null
            return RemoteViews(packageName,R.layout.widget_tile).apply {
                val bitmap=current.render(position)
                setImageViewBitmap(R.id.tile_image,bitmap)
                setContentDescription(R.id.tile_image,current.description(position))
                val factor=resources.displayMetrics.density/current.scale
                val hits=current.checks(position)
                // Click actions must belong to the collection item's RemoteViews, not an added child RemoteViews.
                for(slot in 0 until 24) {
                    val bounds=resources.getIdentifier("check_bounds_$slot","id",packageName)
                    val target=resources.getIdentifier("check_hit_$slot","id",packageName)
                    val hit=hits.getOrNull(slot)
                    setViewVisibility(bounds,if(hit==null) android.view.View.GONE else android.view.View.VISIBLE)
                    if(hit!=null) {
                        setViewPadding(bounds,(hit.rect.left*factor).toInt(),(hit.rect.top*factor).toInt(),((current.width-hit.rect.right)*factor).toInt(),((bitmap.height-hit.rect.bottom)*factor).toInt())
                        setContentDescription(target,(if(hit.checked) "已完成，點一下取消：" else "未完成，點一下勾選：")+hit.label)
                        setOnClickFillInIntent(target,Intent().putExtra("noteId",current.note.id).putExtra("checkId",hit.id))
                    }
                }
            }
        }
        override fun getLoadingView(): RemoteViews?=null
        override fun getViewTypeCount()=1
        override fun getItemId(position: Int)=position.toLong()
        override fun hasStableIds()=false
    }
}
