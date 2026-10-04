package tw.local.memonote.model

import java.util.Locale

/** One palette for text and solid backgrounds, stored in the existing background field. */
object NoteColors {
    val palette = listOf("墨" to 0xff302b3e.toInt(), "莓" to 0xffd43375.toInt(),
        "紫" to 0xff8440cc.toInt(), "藍" to 0xff2371c7.toInt(), "綠" to 0xff10846f.toInt(),
        "金" to 0xffb2730a.toInt(), "白" to 0xffffffff.toInt())
    fun backgroundRef(color: Int): String = "solid_" + String.format(Locale.ROOT,"%08X",color)
    fun backgroundColor(ref: String): Int? = ref.takeIf { it.matches(Regex("solid_[fF]{2}[0-9a-fA-F]{6}")) }
        ?.removePrefix("solid_")?.toLongOrNull(16)?.toInt()
}
