package tw.local.memonote.model

/**
 * Keeps ordered-list prefixes as ordinary note text while updating them after edits.
 * Changes are returned in stages so an EditText can apply them without replacing
 * its full Editable (and therefore preserve image, sticker, checklist, and style spans).
 */
object OrderedListEditor {
    data class Replacement(val start: Int, val end: Int, val text: String)

    data class UserEdit(
        val beforeText: String,
        val start: Int,
        val removedCount: Int,
        val insertedText: String
    )

    data class Plan(
        val text: String,
        val selection: Int,
        val stages: List<List<Replacement>>
    )

    private data class NumberedLine(
        val numberStart: Int,
        val numberEnd: Int,
        val number: Int
    )

    private data class SourceLine(
        val start: Int,
        val end: Int,
        val content: String,
        val number: Int?
    )

    private val numberedLine = Regex("^([ \\t]*)([0-9]+)\\.([ \\t]*)(.*)$")

    /**
     * Plans local edits for the current text. [enterNewlineAt] is the index of a
     * newly typed newline, when the edit came from pressing Enter.
     */
    fun plan(
        text: String,
        selection: Int,
        enterNewlineAt: Int? = null,
        userEdit: UserEdit? = null
    ): Plan {
        var updatedText = text
        var updatedSelection = selection.coerceIn(0, text.length)
        val stages = mutableListOf<List<Replacement>>()

        val enterEdit = enterNewlineAt?.let { findEnterEdit(updatedText, it) }
        if (enterEdit != null) {
            val stage = listOf(enterEdit)
            stages += stage
            updatedText = updatedText.replaceRange(enterEdit.start, enterEdit.end, enterEdit.text)
            updatedSelection = moveSelection(updatedSelection, enterEdit)
        }

        val emptyRowEdits = userEdit?.let { findDeletedEmptyRowEdit(updatedText, it) }.orEmpty()
        if (emptyRowEdits.isNotEmpty()) {
            emptyRowEdits.forEach { edit ->
                updatedText = updatedText.replaceRange(edit.start, edit.end, edit.text)
                updatedSelection = moveSelection(updatedSelection, edit)
            }
            stages += emptyRowEdits
        }

        val shouldNormalize = enterEdit != null ||
            emptyRowEdits.isNotEmpty() ||
            userEdit?.let { touchesNumberedLine(updatedText, it) } == true
        val numberEdits = if (shouldNormalize) {
            findRenumberEdits(updatedText).sortedByDescending { it.start }
        } else {
            emptyList()
        }
        if (numberEdits.isNotEmpty()) {
            numberEdits.forEach { edit ->
                updatedText = updatedText.replaceRange(edit.start, edit.end, edit.text)
                updatedSelection = moveSelection(updatedSelection, edit)
            }
            stages += numberEdits
        }

        return Plan(updatedText, updatedSelection.coerceIn(0, updatedText.length), stages)
    }

    private fun findEnterEdit(text: String, newlineAt: Int): Replacement? {
        if (newlineAt !in text.indices || text[newlineAt] != '\n') return null

        val lineStart = text.lastIndexOf('\n', newlineAt - 1) + 1
        val line = text.substring(lineStart, newlineAt)
        val match = numberedLine.matchEntire(line) ?: return null
        val indent = match.groupValues[1]
        val number = match.groupValues[2].toIntOrNull() ?: return null
        val content = match.groupValues[4]
        val numberStart = lineStart + indent.length

        if (content.isBlank() && hasNumberedPreviousLine(text, lineStart)) {
            // An empty continuation item followed by Enter exits the list.
            return Replacement(numberStart, newlineAt, "")
        }

        if (number == Int.MAX_VALUE) return null
        val nextPrefix = indent + (number + 1).toString() + ". "
        return Replacement(newlineAt + 1, newlineAt + 1, nextPrefix)
    }

    private fun hasNumberedPreviousLine(text: String, lineStart: Int): Boolean {
        if (lineStart <= 0) return false
        val previousEnd = lineStart - 1
        val previousStart = text.lastIndexOf('\n', previousEnd - 1) + 1
        val previousLine = text.substring(previousStart, previousEnd)
        return numberedLine.matchEntire(previousLine)?.groupValues?.get(2)?.toIntOrNull() != null
    }

    /**
     * When the user deletes all text in one numbered row, Android may leave its
     * newline behind. Remove that now-empty row only when the edit proves it was
     * a numbered item and it sits between two numbered rows.
     */
    private fun findDeletedEmptyRowEdit(text: String, edit: UserEdit): List<Replacement> {
        if (edit.removedCount <= 0 || edit.insertedText.isNotEmpty()) return emptyList()
        val removedEnd = edit.start + edit.removedCount

        for (line in sourceLines(edit.beforeText)) {
            val match = numberedLine.matchEntire(line.content) ?: continue
            val numberStart = line.start + match.groupValues[1].length
            if (edit.start < line.start || edit.start > line.end) continue
            if (edit.start > numberStart || removedEnd < line.end) continue

            val afterIndex = edit.start.coerceIn(0, text.length)
            val emptyLineStart = text.lastIndexOf('\n', afterIndex - 1) + 1
            val emptyLineEnd = text.indexOf('\n', emptyLineStart).let { if (it < 0) text.length else it }
            if (text.substring(emptyLineStart, emptyLineEnd).isNotBlank()) continue
            if (emptyLineStart == 0 || emptyLineEnd >= text.length) continue

            val previousEnd = emptyLineStart - 1
            val previousStart = text.lastIndexOf('\n', previousEnd - 1) + 1
            val previousLine = text.substring(previousStart, previousEnd)
            val nextStart = emptyLineEnd + 1
            val nextEnd = text.indexOf('\n', nextStart).let { if (it < 0) text.length else it }
            val nextLine = text.substring(nextStart, nextEnd)
            if (isNumberedLine(previousLine) && isNumberedLine(nextLine)) {
                // Keep the preceding newline and remove the blank row plus its delimiter.
                return listOf(Replacement(emptyLineStart, nextStart, ""))
            }
        }
        return emptyList()
    }

    private fun touchesNumberedLine(text: String, edit: UserEdit): Boolean {
        if (edit.removedCount > 0) {
            val removedEnd = edit.start + edit.removedCount
            val touchedBefore = sourceLines(edit.beforeText).any { line ->
                line.number != null && edit.start <= line.end && removedEnd >= line.start
            }
            if (touchedBefore) return true
        }

        if (edit.insertedText.contains('\uFFFC')) return false
        val position = edit.start.coerceIn(0, text.length)
        return sourceLines(text).any { line ->
            line.number != null && position in line.start..line.end
        }
    }

    private fun isNumberedLine(line: String): Boolean =
        numberedLine.matchEntire(line)?.groupValues?.get(2)?.toIntOrNull() != null

    private fun sourceLines(text: String): List<SourceLine> {
        val lines = mutableListOf<SourceLine>()
        var lineStart = 0
        while (lineStart <= text.length) {
            val newlineAt = text.indexOf('\n', lineStart)
            val lineEnd = if (newlineAt < 0) text.length else newlineAt
            val content = text.substring(lineStart, lineEnd)
            val match = numberedLine.matchEntire(content)
            lines += SourceLine(
                start = lineStart,
                end = lineEnd,
                content = content,
                number = match?.groupValues?.get(2)?.toIntOrNull()
            )
            if (newlineAt < 0) break
            lineStart = newlineAt + 1
        }
        return lines
    }

    private fun findRenumberEdits(text: String): List<Replacement> {
        val edits = mutableListOf<Replacement>()
        val group = mutableListOf<NumberedLine>()

        fun flushGroup() {
            if (group.size > 1) {
                val firstNumber = group.first().number.toLong()
                group.forEachIndexed { index, line ->
                    val expected = firstNumber + index
                    if (expected <= Int.MAX_VALUE && line.number.toLong() != expected) {
                        edits += Replacement(line.numberStart, line.numberEnd, expected.toString())
                    }
                }
            }
            group.clear()
        }

        var lineStart = 0
        while (lineStart <= text.length) {
            val newlineAt = text.indexOf('\n', lineStart)
            val lineEnd = if (newlineAt < 0) text.length else newlineAt
            val lineText = text.substring(lineStart, lineEnd)
            val match = numberedLine.matchEntire(lineText)
            val number = match?.groupValues?.get(2)?.toIntOrNull()

            if (match != null && number != null) {
                val indentLength = match.groupValues[1].length
                val numberLength = match.groupValues[2].length
                group += NumberedLine(
                    numberStart = lineStart + indentLength,
                    numberEnd = lineStart + indentLength + numberLength,
                    number = number
                )
            } else {
                flushGroup()
            }

            if (newlineAt < 0) break
            lineStart = newlineAt + 1
        }
        flushGroup()
        return edits
    }

    private fun moveSelection(selection: Int, edit: Replacement): Int {
        return when {
            selection < edit.start -> selection
            edit.start < edit.end && selection == edit.start -> selection
            selection >= edit.end -> selection + edit.text.length - (edit.end - edit.start)
            else -> edit.start + edit.text.length
        }
    }
}
