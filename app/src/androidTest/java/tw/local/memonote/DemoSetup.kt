package tw.local.memonote

import android.app.Instrumentation
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import tw.local.memonote.data.*
import tw.local.memonote.model.*
import tw.local.memonote.rich.*

/** Explicit opt-in fixture runner, only installed with the test APK. Never runs in the shipped app. */
class DemoSetup: androidx.test.runner.AndroidJUnitRunner() {
    private var demo=false
    override fun onCreate(arguments: Bundle?) { demo=arguments?.getString("seedDemo")=="true"; super.onCreate(arguments) }
    override fun onStart() {
        if(!demo) { super.onStart(); return }
        val c=targetContext
        val text=SpannableStringBuilder("把今天，寫成喜歡的樣子。\n\n小小的陪伴，大大的好心情 ✨\n\uFFFC  \uFFFC\n\n今天想完成的事\n☐ 喝一杯喜歡的咖啡\n☐ 留一點時間給自己\n☐ 看一場期待的直播\n\n記得：慢慢來，也是在前進。\n\n")
        RichText.format(text,0,13){TextStyle(rainbow=true,glow=true)}
        val pink=text.indexOf("小小"); RichText.format(text,pink,pink+14){TextStyle(0xffbf3779.toInt(),false,true)}
        val first=text.indexOf('\uFFFC'); val second=text.indexOf('\uFFFC',first+1)
        text.setSpan(RichText.sticker(c,"asset:stickers/legacy_1.png",76),first,first+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(RichText.sticker(c,"asset:stickers/legacy_0.png",76),second,second+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        for(at in text.indices) if(text[at]=='☐') { text.replace(at,at+1,"\uFFFC"); text.setSpan(CheckSpan("demo-$at",false,36),at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        val photo=android.graphics.Bitmap.createBitmap(600,360,android.graphics.Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(photo); val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader=android.graphics.LinearGradient(0f,0f,600f,360f,0xffa6dbff.toInt(),0xfff8d6e6.toInt(),android.graphics.Shader.TileMode.CLAMP); canvas.drawPaint(paint)
        paint.shader=null; paint.color=0xfffff2c4.toInt(); canvas.drawCircle(460f,90f,44f,paint)
        paint.color=0xff85b7a4.toInt(); canvas.drawOval(-100f,200f,450f,550f,paint); paint.color=0xff62978d.toInt(); canvas.drawOval(230f,180f,770f,510f,paint)
        java.io.File(c.filesDir,"demo-landscape.png").outputStream().use { photo.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; photo.recycle()
        val photoAt=text.length; text.append("\uFFFC\n\n")
        text.setSpan(RichText.sticker(c,"file:demo-landscape.png",220,220,true),photoAt,photoAt+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.append((1..35).joinToString("\n") { "第 $it 個小日常：好好生活，也好好喜歡。" }); text.append("\n\n已經讀到最後一行了 ✦")
        val id=NoteStore(c).use { it.save(Note(title="我的小小日常 ✦",body=text.toString(),formatting=RichText.encode(text),background="sakura",fade=45)) }
        NoteStore(c).use { it.save(Note(title="下次想做的事",body="找一間舒服的咖啡店\n整理喜歡的歌單\n把值得記住的事寫下來",background="ocean",fade=50)) }
        finish(0,Bundle().apply { putLong("noteId",id) })
    }
}
