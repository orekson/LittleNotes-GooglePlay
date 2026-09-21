package tw.local.memonote.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.ScaleGestureDetector
import android.widget.*
import tw.local.memonote.data.ImageFiles

object ImageSizeDialog {
    fun show(activity: Activity,ref: String,initial: Int,onApply: (Int)->Unit) {
        var size=initial.coerceIn(24,480)
        val content=Ui.column(activity); Ui.pad(content,16)
        val label=Ui.label(activity,"",14f); content.addView(label)
        val frame=FrameLayout(activity); content.addView(frame,LinearLayout.LayoutParams(-1,Ui.dp(activity,240)))
        val image=ImageView(activity).apply { setImageBitmap(ImageFiles.load(activity,ref)); scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="雙指縮放圖片" }
        frame.addView(image,FrameLayout.LayoutParams(Ui.dp(activity,size),Ui.dp(activity,size),android.view.Gravity.CENTER))
        val slider=SeekBar(activity).apply { max=456; progress=size-24; contentDescription="圖片大小" }
        fun refresh() { label.text="圖片大小：${size} dp"; image.layoutParams=FrameLayout.LayoutParams(Ui.dp(activity,size.coerceAtMost(230)),Ui.dp(activity,size.coerceAtMost(230)),android.view.Gravity.CENTER) }
        slider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?,p: Int,user: Boolean) { size=p+24; refresh() }
            override fun onStartTrackingTouch(s: SeekBar?)=Unit
            override fun onStopTrackingTouch(s: SeekBar?)=Unit
        })
        val pinch=ScaleGestureDetector(activity,object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean { size=(size*detector.scaleFactor).toInt().coerceIn(24,480); slider.progress=size-24; refresh(); return true }
        })
        frame.setOnTouchListener { _,event -> pinch.onTouchEvent(event); true }
        content.addView(slider); content.addView(Ui.label(activity,"拖動滑桿或雙指縮放。\n保留原圖比例；超過筆記寬度會自動縮小。",12f,Ui.muted)); refresh()
        AlertDialog.Builder(activity).setTitle("調整圖片大小").setView(content).setNegativeButton("取消",null).setPositiveButton("套用") { _,_-> onApply(size) }.show()
    }
}
