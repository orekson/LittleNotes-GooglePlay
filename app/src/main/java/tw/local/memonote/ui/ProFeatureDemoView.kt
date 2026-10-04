package tw.local.memonote.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small, looping illustrations that explain Pro features without depending on remote media. */
class ProFeatureDemoView(context: Context, private val demo: Demo) : View(context) {
    enum class Demo { WIDGET, CLOUD, REMINDER, APPEARANCE, HISTORY }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        contentDescription = AppLanguage.text(context, when (demo) {
            Demo.WIDGET -> "桌面 Widget 功能動畫示意"
            Demo.CLOUD -> "雲端備份功能動畫示意"
            Demo.REMINDER -> "時間提醒功能動畫示意"
            Demo.APPEARANCE -> "筆記外觀功能動畫示意"
            Demo.HISTORY -> "筆記版本歷史動畫示意"
        })
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimator()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncAnimator()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncAnimator()
    }

    private fun syncAnimator() {
        val shouldRun = isAttachedToWindow && visibility == VISIBLE && windowVisibility == VISIBLE
        if (shouldRun && !animator.isStarted) animator.start()
        else if (!shouldRun && animator.isStarted) animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        canvas.save()
        canvas.scale(density, density)
        val w = width / density
        val h = height / density
        fill(0xfff2edfb.toInt())
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 16f, 16f, paint)
        fill(0xffe9e1f5.toInt(), 100)
        canvas.drawCircle(w - 20f, 13f, 21f, paint)
        canvas.drawCircle(w - 3f, h - 3f, 16f, paint)
        when (demo) {
            Demo.WIDGET -> drawWidget(canvas, w, h)
            Demo.CLOUD -> drawCloud(canvas, w, h)
            Demo.REMINDER -> drawReminder(canvas, w, h)
            Demo.APPEARANCE -> drawAppearance(canvas, w, h)
            Demo.HISTORY -> drawHistory(canvas, w, h)
        }
        canvas.restore()
    }

    private fun drawWidget(canvas: Canvas, w: Float, h: Float) {
        val left = 13f
        val top = 9f
        val widgetWidth = w * .58f
        fill(Color.WHITE)
        canvas.drawRoundRect(left, top, left + widgetWidth, h - 9f, 12f, 12f, paint)
        text(canvas, "小小筆記", left + 10f, top + 15f, 8f, 0xff82778f.toInt())
        val palette = intArrayOf(0xffffe5ed.toInt(), 0xffdff2e8.toInt(), 0xffe5e5ff.toInt())
        val blend = phase * 3f
        val index = blend.toInt().coerceAtMost(2)
        val background = mix(palette[index], palette[(index + 1) % palette.size], blend - index)
        fill(background)
        canvas.drawRoundRect(left + 7f, top + 22f, left + widgetWidth - 7f, h - 16f, 8f, 8f, paint)
        text(canvas, "今天，慢慢來", left + 14f, top + 39f, 10f, Ui.ink, true)
        drawCheck(canvas, left + 15f, top + 50f, 0xff8b69c6.toInt())
        text(canvas, "寫下靈感", left + 24f, top + 53f, 8f, Ui.muted)
        drawCheck(canvas, left + 15f, top + 63f, 0xff8b69c6.toInt())
        text(canvas, "放到桌面", left + 24f, top + 66f, 8f, Ui.muted)

        val dateLeft = left + widgetWidth + 15f
        fill(Color.WHITE)
        canvas.drawRoundRect(dateLeft, 15f, w - 13f, h - 15f, 10f, 10f, paint)
        text(canvas, "日期排程", dateLeft + 9f, 32f, 8f, Ui.muted)
        fill(0xffeee5fc.toInt())
        canvas.drawRoundRect(dateLeft + 8f, 39f, w - 21f, 59f, 7f, 7f, paint)
        text(canvas, "07 / 12", dateLeft + 13f, 52f, 8f, Ui.purple, true)
        fill(0xff9a7bce.toInt())
        val pulse = 2f + 1.3f * (0.5f + 0.5f * sin(phase * 2f * PI).toFloat())
        canvas.drawCircle(w - 24f, h - 25f, pulse, paint)
    }

    private fun drawCloud(canvas: Canvas, w: Float, h: Float) {
        val cy = h * .52f
        val phone = RectF(17f, 13f, 63f, h - 13f)
        fill(Color.WHITE)
        canvas.drawRoundRect(phone, 9f, 9f, paint)
        fill(0xffeee7f8.toInt())
        canvas.drawRoundRect(phone.left + 5f, phone.top + 8f, phone.right - 5f, phone.bottom - 8f, 5f, 5f, paint)
        text(canvas, "筆記", phone.left + 10f, phone.top + 22f, 8f, Ui.purple, true)
        fill(0xffbda9de.toInt())
        canvas.drawRoundRect(phone.left + 10f, phone.top + 29f, phone.right - 10f, phone.top + 32f, 2f, 2f, paint)
        canvas.drawRoundRect(phone.left + 10f, phone.top + 37f, phone.right - 7f, phone.top + 40f, 2f, 2f, paint)

        val cloudX = w - 45f
        stroke(0xffb6a2d8.toInt(), 2f)
        canvas.drawLine(phone.right + 3f, cy, cloudX - 22f, cy, paint)
        val packetX = phone.right + 6f + phase * (cloudX - phone.right - 32f)
        fill(0xff906bc9.toInt())
        canvas.drawRoundRect(packetX - 5f, cy - 5f, packetX + 5f, cy + 5f, 3f, 3f, paint)

        fill(Color.WHITE)
        canvas.drawCircle(cloudX - 12f, cy + 1f, 13f, paint)
        canvas.drawCircle(cloudX + 4f, cy - 8f, 17f, paint)
        canvas.drawCircle(cloudX + 21f, cy + 2f, 12f, paint)
        canvas.drawRoundRect(cloudX - 23f, cy, cloudX + 31f, cy + 15f, 7f, 7f, paint)
        text(canvas, "Drive", cloudX - 12f, cy + 4f, 7f, Ui.purple, true)
        text(canvas, "加密備份", w - 64f, h - 12f, 8f, Ui.muted)
    }

    private fun drawReminder(canvas: Canvas, w: Float, h: Float) {
        val cx = w * .23f
        val cy = h * .5f
        stroke(0xff9773cc.toInt(), 3f)
        canvas.drawCircle(cx, cy, 29f, paint)
        stroke(0xffcfc1e5.toInt(), 1.4f)
        for (i in 0 until 12) {
            val angle = i * PI / 6.0
            val inner = 23f
            val outer = if (i % 3 == 0) 27f else 25f
            canvas.drawLine(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer, paint)
        }
        val angle = phase * 2f * PI
        stroke(Ui.purple, 2.5f)
        canvas.drawLine(cx, cy, cx + cos(angle - PI / 2).toFloat() * 16f,
            cy + sin(angle - PI / 2).toFloat() * 16f, paint)
        canvas.drawLine(cx, cy, cx + cos(angle * 12f - PI / 2).toFloat() * 21f,
            cy + sin(angle * 12f - PI / 2).toFloat() * 21f, paint)
        fill(Ui.purple)
        canvas.drawCircle(cx, cy, 3f, paint)

        val drift = sin(phase * 2f * PI).toFloat() * 2f
        fill(Color.WHITE)
        canvas.drawRoundRect(w * .47f, 15f + drift, w - 13f, h - 15f + drift, 11f, 11f, paint)
        fill(0xff8c6bc2.toInt())
        canvas.drawCircle(w * .47f + 14f, h * .5f, 5f, paint)
        text(canvas, "今天  18:30", w * .47f + 26f, h * .5f - 2f, 9f, Ui.ink, true)
        text(canvas, "記得帶傘回家", w * .47f + 26f, h * .5f + 13f, 8f, Ui.muted)
    }

    private fun drawAppearance(canvas: Canvas, w: Float, h: Float) {
        val left = 14f
        val top = 12f
        val right = w - 14f
        val bottom = h - 12f
        val t = 0.5f + 0.5f * sin(phase * 2f * PI).toFloat()
        fill(mix(0xffffe8e1.toInt(), 0xffe5e0ff.toInt(), t))
        canvas.drawRoundRect(left, top, right, bottom, 11f, 11f, paint)
        text(canvas, "一點色彩，剛剛好", left + 13f, top + 20f, 9f, Ui.muted)
        val label = "彩虹柔光"
        val rainbow = intArrayOf(0xffd46e9f.toInt(), 0xff8d69c6.toInt(), 0xff5f88c9.toInt(), 0xff5b9a82.toInt())
        var x = left + 13f
        label.forEachIndexed { index, character ->
            text(canvas, character.toString(), x, top + 43f, 15f, rainbow[index % rainbow.size], true)
            x += 17f
        }
        val swatches = intArrayOf(0xffffd7df.toInt(), 0xffdcd2f3.toInt(), 0xffd8ebdf.toInt(), 0xfff6e4be.toInt())
        swatches.forEachIndexed { index, color ->
            fill(color)
            canvas.drawCircle(left + 18f + index * 24f, bottom - 10f, 5f, paint)
        }
        val fade = 0.35f + 0.25f * t
        fill(Color.WHITE, (fade * 210).toInt())
        canvas.drawRoundRect(right - 66f, top + 11f, right - 11f, bottom - 11f, 8f, 8f, paint)
        text(canvas, "淡化", right - 52f, top + 30f, 8f, Ui.purple, true)
        fill(0xffc9b7e6.toInt())
        canvas.drawRoundRect(right - 53f, top + 38f, right - 22f, top + 42f, 2f, 2f, paint)
        fill(Ui.purple)
        canvas.drawCircle(right - 53f + 31f * t, top + 40f, 4f, paint)
    }

    private fun drawHistory(canvas: Canvas, w: Float, h: Float) {
        val centerX = w * .5f
        val centerY = h * .42f
        val cards = listOf(
            Triple(-12f, 0xffeee6fb.toInt(), "昨天"),
            Triple(0f, 0xfffff0e7.toInt(), "今天"),
            Triple(12f, 0xffe4f2ea.toInt(), "已儲存")
        )
        cards.forEachIndexed { index, (offset, color, label) ->
            val reveal = (phase * 3f - index).coerceIn(0f, 1f)
            val drift = (1f - reveal) * 8f
            canvas.save()
            canvas.rotate(offset, centerX, centerY)
            fill(color)
            canvas.drawRoundRect(centerX - 35f + drift, centerY - 24f + drift,
                centerX + 35f + drift, centerY + 24f + drift, 8f, 8f, paint)
            text(canvas, label, centerX - 23f + drift, centerY - 5f + drift, 8f, Ui.purple, true)
            stroke(0xffbba9d7.toInt(), 2f)
            canvas.drawLine(centerX - 22f + drift, centerY + 5f + drift,
                centerX + 18f + drift, centerY + 5f + drift, paint)
            canvas.restore()
        }
        val y = h - 17f
        stroke(0xffc8bbdd.toInt(), 2f)
        canvas.drawLine(28f, y, w - 28f, y, paint)
        for (i in 0..4) {
            fill(if (phase * 4f >= i) 0xff9874cf.toInt() else 0xffd5cce3.toInt())
            canvas.drawCircle(30f + i * (w - 60f) / 4f, y, 3.4f, paint)
        }
    }

    private fun drawCheck(canvas: Canvas, x: Float, y: Float, color: Int) {
        stroke(color, 1.4f)
        canvas.drawCircle(x, y - 3f, 4f, paint)
        canvas.drawLine(x - 2f, y - 3f, x - .5f, y - 1.5f, paint)
        canvas.drawLine(x - .5f, y - 1.5f, x + 2.5f, y - 4.5f, paint)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float,
                     color: Int, bold: Boolean = false) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = 255
        paint.textSize = size
        paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        paint.clearShadowLayer()
        canvas.drawText(value, x, y, paint)
    }

    private fun fill(color: Int, alpha: Int = 255) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha
        paint.clearShadowLayer()
    }

    private fun stroke(color: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.alpha = 255
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.clearShadowLayer()
    }

    private fun mix(start: Int, end: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        fun channel(from: Int, to: Int) = (from * (1f - t) + to * t).toInt()
        return Color.argb(
            channel(Color.alpha(start), Color.alpha(end)),
            channel(Color.red(start), Color.red(end)),
            channel(Color.green(start), Color.green(end)),
            channel(Color.blue(start), Color.blue(end))
        )
    }
}
