package tw.local.memonote.data

/** Shared naming and compatibility rules for flavor-provided sticker assets. */
object StickerAssets {
    fun sortStickerFiles(files: Iterable<String>): List<String> = files.sortedWith(
        compareBy<String> { numericSuffix(it) ?: Int.MAX_VALUE }.thenBy { it },
    )

    /** Returns the stored path first, followed by the current stable path when possible. */
    fun assetCandidates(path: String): List<String> {
        val index = numericSuffix(path) ?: return listOf(path)
        val slash = path.lastIndexOf('/')
        val directory = if (slash >= 0) path.substring(0, slash + 1) else ""
        val stable = "$directory" + "sticker_${index.toString().padStart(2, '0')}.png"
        return if (stable == path) listOf(path) else listOf(path, stable)
    }

    private fun numericSuffix(path: String): Int? {
        val fileName = path.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        val suffix = stem.takeLastWhile { it.isDigit() }
        return suffix.toIntOrNull()
    }
}
