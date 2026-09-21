package tw.local.memonote.rich

import android.content.Context
import android.graphics.*
import android.text.*
import tw.local.memonote.data.*

class NoteRenderer(context: Context,val note: Note,val width: Int,val scale: Float) {
    private val padding=(12*scale).toInt()
    private val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize=16*scale*(context.resources.displayMetrics.scaledDensity/context.resources.displayMetrics.density)
        color=0xff302b3e.toInt()
        typeface=Typeface.create("sans-serif",Typeface.NORMAL)
    }
    val text=RichText.decode(context,note.body,note.formatting,(76*scale).toInt(),(width-padding*2).coerceAtLeast(24))
    private val layout=StaticLayout.Builder.obtain(text,0,text.length,paint,(width-padding*2).coerceAtLeast(24))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(5*scale,1f).setIncludePad(true).build()
    data class Tile(val firstLine: Int,val lastLine: Int,val top: Int,val bottom: Int,val start: Int,val end: Int)
    val tiles: List<Tile> = buildList {
        var line=0
        while(line<layout.lineCount) {
            val first=line; val top=layout.getLineTop(first)
            line++
            while(line<layout.lineCount && layout.getLineBottom(line)-top<180*scale && text.getSpans(layout.getLineStart(first),layout.getLineEnd(line),CheckSpan::class.java).size<=24) line++
            val bottom=layout.getLineBottom(line-1)
            var partTop=top
            while(partTop<bottom) {
                val partBottom=minOf(bottom,partTop+(180*scale).toInt().coerceAtLeast(1))
                add(Tile(first,line-1,partTop,partBottom,layout.getLineStart(first),layout.getLineEnd(line-1)))
                partTop=partBottom
            }
        }
    }
    data class CheckHit(val id: String,val checked: Boolean,val rect: Rect,val label: String)
    fun checks(index: Int): List<CheckHit> {
        val tile=tiles[index]; val extra=if(index==0) padding else 0
        return text.getSpans(tile.start,tile.end,CheckSpan::class.java).mapNotNull { span ->
            val at=text.getSpanStart(span); val line=layout.getLineForOffset(at)
            val baseline=layout.getLineBaseline(line)
            val top=baseline+span.topOffset(paint); val bottom=top+span.size
            if(bottom<=tile.top || top>=tile.bottom) null else {
                val x=padding+(SpanGeometry.bounds(layout,text,at)?.left ?: 0f).toInt()
                CheckHit(span.id,span.checked,Rect(x,maxOf(top,tile.top)-tile.top+extra,(x+span.size).coerceAtMost(width),minOf(bottom,tile.bottom)-tile.top+extra),text.subSequence(at+1,layout.getLineEnd(line)).toString().trim())
            }
        }
    }
    fun render(index: Int): Bitmap {
        val t=tiles[index]
        val topExtra=if(index==0) padding else 0
        val bottomExtra=if(index==tiles.lastIndex) padding else 0
        return Bitmap.createBitmap(width,(t.bottom-t.top+topExtra+bottomExtra).coerceAtLeast(1),Bitmap.Config.ARGB_8888).also { b ->
            val canvas=Canvas(b); canvas.translate(padding.toFloat(),(topExtra-t.top).toFloat()); layout.draw(canvas)
        }
    }
    fun description(index: Int): String { val t=tiles[index]; return note.body.substring(t.start,t.end).replace("\uFFFC"," ") }
    companion object {
        fun background(context: Context,ref: String,fade: Int,width: Int,height: Int): Bitmap {
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); val c=Canvas(bitmap)
            c.drawColor(0xfffffbf6.toInt())
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha=((100-fade.coerceIn(0,100))*2.55f).toInt() }
            val photo=ImageFiles.load(context,ref)
            if(photo!=null) {
                val f=maxOf(width.toFloat()/photo.width,height.toFloat()/photo.height)
                val w=photo.width*f; val h=photo.height*f
                c.drawBitmap(photo,null,RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),p); photo.recycle()
            } else {
                val colors=when(ref) {
                    "sakura" -> intArrayOf(0xfff7bfdc.toInt(),0xffe5d1fa.toInt(),0xffffedd8.toInt())
                    "ocean" -> intArrayOf(0xffb3e8f8.toInt(),0xffc4d6fc.toInt(),0xffe0c9f9.toInt())
                    "night" -> intArrayOf(0xff30234d.toInt(),0xff6e5095.toInt(),0xffcf96b4.toInt())
                    else -> intArrayOf(0xfffff7ea.toInt(),0xfff3eaf9.toInt(),0xfffffdf7.toInt())
                }
                p.shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),colors,null,Shader.TileMode.CLAMP)
                c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p)
                p.shader=null; p.color=Color.WHITE; p.alpha=(48*(100-fade)/100)
                for(i in 0..8) c.drawCircle(width*(i%3)/2f,height*i/8f,width*.12f,p)
            }
            return bitmap
        }
    }
}
