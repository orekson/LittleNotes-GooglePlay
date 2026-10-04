package tw.local.memonote.widget

import android.app.Activity
import android.app.AlertDialog
import android.graphics.*
import android.widget.*
import tw.local.memonote.data.Note
import tw.local.memonote.rich.NoteRenderer
import tw.local.memonote.ui.*
import tw.local.memonote.entitlement.PremiumFeature

object WidgetStyleDialog {
    fun show(activity: Activity, initial: WidgetStyle, note: Note?, apply: (WidgetStyle) -> Unit) {
        if (!ProUi.require(activity,PremiumFeature.ADVANCED_WIDGET_CUSTOMIZATION)) return
        var value=initial
        val content=Ui.column(activity).apply { Ui.pad(this,16) }
        val preview=ImageView(activity).apply { adjustViewBounds=true }
        content.addView(preview,LinearLayout.LayoutParams(-1,Ui.dp(activity,160)))
        fun refresh() {
            val sample=note?.takeUnless { it.isLocked } ?: Note(title="預覽",body="今天，慢慢來\n寫下值得記住的小事。")
            val bitmap=Bitmap.createBitmap(640,300,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            canvas.clipPath(Path().apply { addRoundRect(RectF(0f,0f,640f,300f),value.corner*2f,value.corner*2f,Path.Direction.CW) })
            val bg=NoteRenderer.background(activity,sample.background,sample.fade,640,300)
            canvas.drawBitmap(bg,0f,0f,Paint().apply { alpha=value.opacity*255/100 }); bg.recycle()
            val renderer=NoteRenderer(activity,sample,640,2f,WidgetAppearance.textColor(sample.background),
                value.fontSize,value.font,value.padding,value.lineGap,value.showImages)
            if(renderer.tiles.isNotEmpty()) { val tile=renderer.render(0); canvas.drawBitmap(tile,0f,0f,null); tile.recycle() }
            preview.setImageBitmap(bitmap)
        }
        fun slider(label: String, min: Int, max: Int, start: Int, change: (Int)->Unit) {
            val caption=Ui.label(activity,"$label：$start",14f,Ui.ink)
            content.addView(caption)
            content.addView(SeekBar(activity).apply {
                this.max=max-min; progress=start-min
                setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?,p: Int,user: Boolean) {
                        if(user) { change(p+min); caption.text="$label：${p+min}"; refresh() }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?)=Unit
                    override fun onStopTrackingTouch(s: SeekBar?)=Unit
                })
            })
        }
        val corners=Ui.button(activity,"圓角：${value.corner} dp") { }
        corners.setOnClickListener {
            AlertDialog.Builder(activity).setTitle("圓角")
                .setSingleChoiceItems(WidgetStyle.corners.map { "$it dp" }.toTypedArray(),WidgetStyle.corners.indexOf(value.corner)) { dialog,index ->
                    value=value.copy(corner=WidgetStyle.corners[index]); corners.text="圓角：${value.corner} dp"; refresh(); dialog.dismiss()
                }.show()
        }
        content.addView(corners)
        slider("內距 dp",4,32,value.padding) { value=value.copy(padding=it) }
        slider("行距 dp",0,20,value.lineGap) { value=value.copy(lineGap=it) }
        slider("字級 sp",12,28,value.fontSize) { value=value.copy(fontSize=it) }
        slider("背景不透明度 %",10,100,value.opacity) { value=value.copy(opacity=it) }
        val fontNames=listOf("標準字體","襯線字體","等寬字體","細字體")
        val font=Ui.button(activity,"字體：${fontNames[WidgetStyle.fonts.indexOf(value.font)]}") { }
        font.setOnClickListener {
            AlertDialog.Builder(activity).setTitle("字體")
                .setSingleChoiceItems(fontNames.toTypedArray(),WidgetStyle.fonts.indexOf(value.font)) { dialog,index ->
                    value=value.copy(font=WidgetStyle.fonts[index]); font.text="字體：${fontNames[index]}"; refresh(); dialog.dismiss()
                }.show()
        }
        content.addView(font)
        content.addView(Switch(activity).apply {
            text="顯示筆記圖片與貼圖"; isChecked=value.showImages
            setOnCheckedChangeListener { _,checked -> value=value.copy(showImages=checked); refresh() }
        })
        content.addView(Ui.label(activity,"預覽會套用於桌面筆記內容；字型依系統可用字型顯示。",12f,Ui.muted))
        val dialog=AlertDialog.Builder(activity).setTitle("Widget 進階外觀 · Pro")
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("取消",null).setPositiveButton("套用",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(ProUi.require(activity,PremiumFeature.ADVANCED_WIDGET_CUSTOMIZATION)) { apply(value.normalized()); dialog.dismiss() }
        } }
        refresh(); dialog.show()
    }
}
