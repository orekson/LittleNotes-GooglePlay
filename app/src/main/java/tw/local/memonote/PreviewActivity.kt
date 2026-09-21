package tw.local.memonote

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import tw.local.memonote.data.Note
import tw.local.memonote.rich.NoteRenderer
import tw.local.memonote.ui.Ui

class PreviewActivity: Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val note=try { Note.fromJson(java.io.File(cacheDir,"preview.json").readText()) } catch(e: Exception) { finish(); return }
        val root=Ui.root(this)
        val bar=Ui.row(this); Ui.pad(bar,12); bar.addView(Ui.button(this,"返回編輯") { finish() }); bar.addView(Ui.label(this,"小工具預覽",18f,bold=true)); root.addView(bar)
        root.addView(Ui.label(this,"  實際尺寸可在桌面長按調整；長文可上下捲動。",12f,Ui.muted))
        val frame=Ui.column(this); val p=LinearLayout.LayoutParams(-1,0,1f); p.setMargins(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,20)); root.addView(frame,p)
        val title=Ui.label(this,note.displayTitle,20f,bold=true); Ui.pad(title,16); frame.addView(title)
        val list=ListView(this).apply { divider=null; setBackgroundColor(android.graphics.Color.TRANSPARENT) }; frame.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        frame.post {
            val width=frame.width.coerceAtLeast(120); val scale=resources.displayMetrics.density
            frame.background=BitmapDrawable(resources,NoteRenderer.background(this,note.background,note.fade,width.coerceAtMost(600),(frame.height*minOf(1f,600f/width)).toInt().coerceAtLeast(1)))
            val renderer=NoteRenderer(this,note,width,scale)
            list.adapter=object: BaseAdapter() {
                override fun getCount()=renderer.tiles.size
                override fun getItem(position: Int)=renderer.tiles[position]
                override fun getItemId(position: Int)=position.toLong()
                override fun getView(position: Int,convertView: View?,parent: ViewGroup?): View = (convertView as? ImageView ?: ImageView(this@PreviewActivity).apply { adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER; layoutParams=AbsListView.LayoutParams(-1,-2) }).apply { setImageBitmap(renderer.render(position)); contentDescription=renderer.description(position) }
            }
        }
    }
}
