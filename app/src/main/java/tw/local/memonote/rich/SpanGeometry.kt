package tw.local.memonote.rich

import android.graphics.RectF
import android.text.Layout
import android.text.Spanned

object SpanGeometry {
    fun bounds(layout: Layout,text: Spanned,at: Int): RectF? {
        val width=text.getSpans(at,at+1,StickerSpan::class.java).firstOrNull()?.drawable?.bounds?.width()
            ?: text.getSpans(at,at+1,CheckSpan::class.java).firstOrNull()?.size ?: return null
        val line=layout.getLineForOffset(at)
        val caret=layout.getPrimaryHorizontal(at)
        val left=if(layout.isRtlCharAt(at)) caret-width else caret
        return RectF(left,layout.getLineTop(line).toFloat(),left+width,layout.getLineBottom(line).toFloat())
    }
}
