package tw.local.memonote.rich

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderSpan(val id: String, val timeMillis: Long,
                   val rule: tw.local.memonote.reminder.ReminderRule = tw.local.memonote.reminder.ReminderRule()) : ReplacementSpan() {
    private fun caption(): String = "⏰ " + SimpleDateFormat("MM/dd HH:mm", Locale.ROOT).format(Date(timeMillis)) +
        (if(rule.repeat != tw.local.memonote.reminder.Repeat.NONE) " ↻" else "")
    private fun width(paint: Paint): Int = (paint.measureText(caption()) + paint.textSize * 0.65f).toInt()

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int,
                         fm: Paint.FontMetricsInt?): Int = width(paint)

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int,
                      x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val label = caption()
        val w = width(paint).toFloat()
        val oldColor = paint.color
        val oldStyle = paint.style
        val oldShader = paint.shader
        paint.shader = null
        paint.color = 0xffeee5fb.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(x, y + paint.ascent() - 2f, x + w,
            y + paint.descent() + 2f), paint.textSize * .25f, paint.textSize * .25f, paint)
        paint.color = 0xff674495.toInt()
        canvas.drawText(label, x + paint.textSize * .30f, y.toFloat(), paint)
        paint.color = oldColor
        paint.style = oldStyle
        paint.shader = oldShader
    }
}
