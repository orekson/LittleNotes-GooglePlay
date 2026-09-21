package tw.local.memonote.rich

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.*
import android.text.style.CharacterStyle
import android.text.style.ImageSpan
import android.text.style.UpdateAppearance
import org.json.JSONArray
import org.json.JSONObject
import tw.local.memonote.data.ImageFiles
import tw.local.memonote.model.*

class PaintSpan(val style: TextStyle): CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(p: TextPaint) {
        p.color=style.color ?: Color.rgb(48,43,62)
        p.shader=if(style.rainbow) LinearGradient(0f,0f,p.textSize*12,0f,intArrayOf(0xffec4899.toInt(),0xff9955ef.toInt(),0xff3184f4.toInt(),0xff0ca98e.toInt(),0xffeea324.toInt()),null,Shader.TileMode.MIRROR) else null
        if(style.glow) p.setShadowLayer(p.textSize*.20f,0f,0f,style.color ?: 0xffbf60e8.toInt()) else p.clearShadowLayer()
    }
}
class StickerSpan(val ref: String, drawable: android.graphics.drawable.Drawable, val sizeDp: Int=76, val isPhoto: Boolean=false): ImageSpan(drawable,ALIGN_BOTTOM)

class CheckSpan(val id: String, var checked: Boolean, val size: Int): android.text.style.ReplacementSpan() {
    fun topOffset(paint: Paint)=((paint.fontMetrics.ascent+paint.fontMetrics.descent)/2-size/2f).toInt()
    override fun getSize(paint: Paint,text: CharSequence,start: Int,end: Int,fm: Paint.FontMetricsInt?): Int {
        val top=topOffset(paint)
        fm?.let { it.ascent=minOf(it.ascent,top); it.top=minOf(it.top,it.ascent); it.descent=maxOf(it.descent,top+size); it.bottom=maxOf(it.bottom,it.descent) }
        return size
    }
    override fun draw(c: Canvas,text: CharSequence,start: Int,end: Int,x: Float,top: Int,y: Int,bottom: Int,paint: Paint) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG); val gap=size*.20f
        val bottom=y+topOffset(paint)+size
        val box=RectF(x+gap,bottom-size+gap,x+size-gap,bottom-gap)
        p.color=if(checked) 0xff18955e.toInt() else 0xff796a91.toInt()
        p.style=if(checked) Paint.Style.FILL else Paint.Style.STROKE; p.strokeWidth=size*.06f
        c.drawRoundRect(box,size*.09f,size*.09f,p)
        if(checked) { p.color=Color.WHITE; p.style=Paint.Style.STROKE; p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
            c.drawPath(Path().apply { moveTo(x+size*.32f,bottom-size*.49f); lineTo(x+size*.45f,bottom-size*.35f); lineTo(x+size*.70f,bottom-size*.64f) },p) }
    }
}

object RichText {
    fun sticker(context: Context,ref: String,size: Int,sizeDp: Int=76,isPhoto: Boolean=false): StickerSpan {
        val bitmap=ImageFiles.load(context,ref) ?: Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).also { b -> Canvas(b).drawColor(0xffdfd3ee.toInt()) }
        val d=BitmapDrawable(context.resources,bitmap)
        val factor=size.toFloat()/maxOf(bitmap.width,bitmap.height)
        d.setBounds(0,0,(bitmap.width*factor).toInt().coerceAtLeast(1),(bitmap.height*factor).toInt().coerceAtLeast(1))
        return StickerSpan(ref,d,sizeDp,isPhoto)
    }
    fun decode(context: Context,text: String,raw: String,stickerSize: Int,maxWidth: Int=Int.MAX_VALUE): SpannableStringBuilder {
        val result=SpannableStringBuilder(text)
        val json=try { JSONObject(raw) } catch(e: Exception) { JSONObject() }
        val ranges=json.optJSONArray("styles") ?: JSONArray()
        for(i in 0 until ranges.length()) {
            val r=ranges.optJSONObject(i) ?: continue
            val start=r.optInt("start").coerceIn(0,text.length); val end=r.optInt("end").coerceIn(start,text.length)
            if(start<end) result.setSpan(PaintSpan(TextStyle(if(r.has("color")) r.optInt("color") else null,r.optBoolean("rainbow"),r.optBoolean("glow"))),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val stickers=json.optJSONArray("stickers") ?: JSONArray()
        for(i in 0 until stickers.length()) {
            val r=stickers.optJSONObject(i) ?: continue; val at=r.optInt("at",-1)
            val dp=r.optInt("sizeDp",76).coerceIn(24,480)
            if(at in text.indices && text[at]=='\uFFFC') result.setSpan(sticker(context,r.optString("ref"),(stickerSize*dp/76).coerceAtMost(maxWidth).coerceAtLeast(1),dp,r.optBoolean("photo")),at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val checks=json.optJSONArray("checks") ?: JSONArray()
        for(i in 0 until checks.length()) {
            val r=checks.optJSONObject(i) ?: continue; val at=r.optInt("at",-1); val id=r.optString("id")
            if(at in text.indices && text[at]=='\uFFFC' && id.isNotBlank()) result.setSpan(CheckSpan(id,r.optBoolean("checked"),(36f*stickerSize/76).toInt().coerceAtLeast(1)),at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }
    fun encode(text: Spanned): String {
        val styles=JSONArray(); val stickers=JSONArray(); val checks=JSONArray()
        text.getSpans(0,text.length,PaintSpan::class.java).forEach { span ->
            val start=text.getSpanStart(span); val end=text.getSpanEnd(span)
            if(start<end) styles.put(JSONObject().put("start",start).put("end",end).put("color",span.style.color).put("rainbow",span.style.rainbow).put("glow",span.style.glow))
        }
        text.getSpans(0,text.length,StickerSpan::class.java).forEach { span ->
            val at=text.getSpanStart(span)
            if(at in 0 until text.length && text[at]=='\uFFFC') stickers.put(JSONObject().put("at",at).put("ref",span.ref).put("sizeDp",span.sizeDp).put("photo",span.isPhoto))
        }
        text.getSpans(0,text.length,CheckSpan::class.java).forEach { span ->
            val at=text.getSpanStart(span)
            if(at in 0 until text.length && text[at]=='\uFFFC') checks.put(JSONObject().put("at",at).put("id",span.id).put("checked",span.checked))
        }
        return JSONObject().put("version",2).put("styles",styles).put("stickers",stickers).put("checks",checks).toString()
    }
    fun format(text: Editable,start: Int,end: Int,change: (TextStyle)->TextStyle) {
        val old=text.getSpans(0,text.length,PaintSpan::class.java)
        val ranges=old.map { StyleRange(text.getSpanStart(it),text.getSpanEnd(it),it.style) }
        val updated=StyleRanges.apply(ranges,start.coerceIn(0,text.length),end.coerceIn(0,text.length),change)
        old.forEach { text.removeSpan(it) }
        updated.forEach { text.setSpan(PaintSpan(it.style),it.start,it.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
}
