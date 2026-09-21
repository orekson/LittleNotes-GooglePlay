package tw.local.memonote.model

data class TextStyle(val color: Int? = null, val rainbow: Boolean = false, val glow: Boolean = false)
data class StyleRange(val start: Int, val end: Int, val style: TextStyle)
object StyleRanges {
    fun apply(ranges: List<StyleRange>, start: Int, end: Int, change: (TextStyle) -> TextStyle): List<StyleRange> {
        if(start>=end) return ranges
        val points=(ranges.flatMap { listOf(it.start,it.end) }+listOf(start,end)).filter { it>=0 }.distinct().sorted()
        val result=mutableListOf<StyleRange>()
        for(i in 0 until points.lastIndex) {
            val a=points[i]; val b=points[i+1]
            val old=ranges.lastOrNull { it.start<=a && it.end>=b }?.style ?: TextStyle()
            val style=if(a>=start && b<=end) change(old) else old
            if(style==TextStyle()) continue
            val prev=result.lastOrNull()
            if(prev!=null && prev.end==a && prev.style==style) result[result.lastIndex]=prev.copy(end=b)
            else result.add(StyleRange(a,b,style))
        }
        return result
    }
}
