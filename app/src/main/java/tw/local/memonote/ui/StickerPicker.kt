package tw.local.memonote.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.*
import tw.local.memonote.R
import tw.local.memonote.data.ImageFiles
import tw.local.memonote.data.StickerAssets

object StickerPicker {
    fun show(activity: Activity,onPick: (String)->Unit,onImport: ()->Unit) {
        val column=Ui.column(activity); Ui.pad(column,16)
        column.addView(Ui.label(activity,"小小的陪伴，放進你的日常",14f,Ui.muted))
        val grid=GridLayout(activity).apply { columnCount=4 }
        val dialog=AlertDialog.Builder(activity).setTitle(activity.getString(R.string.sticker_picker_title)).setView(column).setNegativeButton(AppLanguage.text(activity,"取消"),null).setNeutralButton(AppLanguage.text(activity,"匯入自己的貼圖")) { _,_-> onImport() }.create()
        val files=StickerAssets.sortStickerFiles((activity.assets.list("stickers") ?: emptyArray()).filter { it.endsWith(".png") })
        for((i,file) in files.withIndex()) {
            val ref="asset:stickers/$file"
            val image=ImageButton(activity).apply {
                setImageBitmap(ImageFiles.load(activity,ref)); scaleType=ImageView.ScaleType.FIT_CENTER
                background=Ui.rounded(0xfff2ebfa.toInt(),16f); contentDescription=activity.getString(R.string.sticker_content_description,i+1)
                setPadding(4,4,4,4)
                setOnClickListener { onPick(ref); dialog.dismiss() }
            }
            grid.addView(image,GridLayout.LayoutParams().apply { width=Ui.dp(activity,64); height=Ui.dp(activity,80); setMargins(3,6,3,6) })
        }
        column.addView(ScrollView(activity).apply { addView(grid) },LinearLayout.LayoutParams(-1,Ui.dp(activity,320))); column.addView(Ui.space(activity,8)); column.addView(Ui.label(activity,activity.getString(R.string.sticker_picker_count,files.size),12f,Ui.muted))
        dialog.show()
    }
}
