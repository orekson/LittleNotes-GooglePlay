package tw.local.memonote.rich

import android.content.Context
import android.text.Editable
import tw.local.memonote.entitlement.*
import tw.local.memonote.model.StyleRange
import tw.local.memonote.model.StyleRanges
import tw.local.memonote.model.TextStyle

/** Mutation boundary for the editor; renderers and restored data remain unconditional. */
object PremiumTextEdits {
    fun format(context: Context, text: Editable, start: Int, end: Int, change: (TextStyle) -> TextStyle): Boolean {
        if (!EntitlementManager.allows(context, PremiumFeature.ADVANCED_TEXT, FeatureOperation.MODIFY)) {
            val ranges = text.getSpans(0, text.length, PaintSpan::class.java).map {
                StyleRange(text.getSpanStart(it), text.getSpanEnd(it), it.style)
            }
            val proposed = StyleRanges.apply(ranges, start, end, change)
            val points = (ranges + proposed).flatMap { listOf(it.start, it.end) }.distinct().sorted()
            for (i in 0 until points.lastIndex) {
                val a = points[i]; val b = points[i + 1]
                val old = ranges.lastOrNull { it.start <= a && it.end >= b }?.style ?: TextStyle()
                val next = proposed.lastOrNull { it.start <= a && it.end >= b }?.style ?: TextStyle()
                if ((next.rainbow && !old.rainbow) || (next.glow && !old.glow) ||
                    ((next.rainbow || next.glow) && next.color != old.color)) return false
            }
        }
        RichText.format(text, start, end, change)
        return true
    }
    fun color(context: Context, text: Editable, start: Int, end: Int, color: Int) {
        val advanced = EntitlementManager.allows(context, PremiumFeature.ADVANCED_TEXT, FeatureOperation.MODIFY)
        RichText.format(text, start, end) { it.copy(color = color, rainbow = false, glow = advanced && it.glow) }
    }
    /** New typing/pasted spans cannot silently inherit or duplicate a paid setting in Free. */
    fun sanitizeInsertion(context: Context, text: Editable, start: Int, count: Int) {
        if (count <= 0 || EntitlementManager.allows(context, PremiumFeature.ADVANCED_TEXT, FeatureOperation.CREATE)) return
        val end = (start + count).coerceAtMost(text.length)
        if (text.getSpans(start,end,PaintSpan::class.java).any { it.style.rainbow || it.style.glow })
            RichText.format(text, start.coerceAtLeast(0), end) { it.copy(rainbow = false, glow = false) }
        text.getSpans(start, end, ReminderSpan::class.java).filter { text.getSpanStart(it) >= start }
            .sortedByDescending { text.getSpanStart(it) }.forEach {
                val at = text.getSpanStart(it); text.removeSpan(it)
                // Preserve a readable pasted time, but do not create a new alarm.
                text.replace(at, at + 1, "⏰ " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT)
                    .format(java.util.Date(it.timeMillis)))
            }
    }
}
