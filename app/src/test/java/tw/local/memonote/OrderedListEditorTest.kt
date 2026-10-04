package tw.local.memonote

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.local.memonote.model.OrderedListEditor

class OrderedListEditorTest {
    @Test
    fun pressingEnterAfterNumberedItemAddsNextNumber() {
        val text = "1. first item\n"
        val result = OrderedListEditor.plan(text, text.length, text.lastIndex)

        assertEquals("1. first item\n2. ", result.text)
        assertEquals(result.text.length, result.selection)
    }

    @Test
    fun pressingEnterOnEmptyContinuationExitsTheList() {
        val text = "1. first item\n2. \n"
        val result = OrderedListEditor.plan(text, text.length, text.lastIndex)

        assertEquals("1. first item\n\n", result.text)
        assertEquals(result.text.length, result.selection)
    }

    @Test
    fun deletingMiddleNumberedRowRenumbersFollowingRows() {
        val before = "1. first item\n2. second item\n3. third item\n4. fourth item\n5. fifth item"
        val deletedRow = "3. third item\n"
        val start = before.indexOf("3. third item")
        val textAfterDeletingThirdRow = before.removeRange(start, start + deletedRow.length)
        val result = OrderedListEditor.plan(
            textAfterDeletingThirdRow,
            start,
            userEdit = OrderedListEditor.UserEdit(before, start, deletedRow.length, "")
        )

        assertEquals(
            "1. first item\n2. second item\n3. fourth item\n4. fifth item",
            result.text
        )
        assertEquals(result.text.indexOf("3. fourth item"), result.selection)
    }

    @Test
    fun deletingRowTextAndLeavingItsBlankLineStillRenumbersFollowingRows() {
        val before = "1. first item\n2. second item\n3. third item\n4. fourth item\n5. fifth item"
        val deletedRow = "3. third item"
        val textAfterDeletingThirdRow =
            "1. first item\n2. second item\n\n4. fourth item\n5. fifth item"
        val result = OrderedListEditor.plan(
            textAfterDeletingThirdRow,
            textAfterDeletingThirdRow.length,
            userEdit = OrderedListEditor.UserEdit(
                beforeText = before,
                start = before.indexOf(deletedRow),
                removedCount = deletedRow.length,
                insertedText = ""
            )
        )

        assertEquals(
            "1. first item\n2. second item\n3. fourth item\n4. fifth item",
            result.text
        )
    }

    @Test
    fun cursorAtRenumberedPrefixStaysBeforeTheNumber() {
        val before = "1. first item\n2. second item\n3. third item\n4. fourth item"
        val deletedRow = "3. third item\n"
        val start = before.indexOf("3. third item")
        val text = before.removeRange(start, start + deletedRow.length)
        val result = OrderedListEditor.plan(
            text,
            start,
            userEdit = OrderedListEditor.UserEdit(before, start, deletedRow.length, "")
        )

        assertEquals("1. first item\n2. second item\n3. fourth item", result.text)
        assertEquals(result.text.indexOf("3. fourth item"), result.selection)
    }

    @Test
    fun insertingMediaOutsideAListDoesNotRenumberThatList() {
        val before = "1. first item\n4. fourth item"
        val after = before + "\uFFFC"
        val result = OrderedListEditor.plan(
            after,
            after.length,
            userEdit = OrderedListEditor.UserEdit(before, before.length, 0, "\uFFFC")
        )

        assertEquals(after, result.text)
        assertEquals(0, result.stages.size)
    }

    @Test
    fun separateListsKeepTheirStartingNumbers() {
        val text = "1. first\n2. second\n\n4. another list\n5. next"
        val result = OrderedListEditor.plan(text, text.length)

        assertEquals(text, result.text)
    }
}
