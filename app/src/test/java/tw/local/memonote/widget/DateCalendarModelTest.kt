package tw.local.memonote.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DateCalendarModelTest {
    @Test
    fun assignedDateCellCarriesItsNoteTitle() {
        val assignedDate = LocalDate.of(2026, 9, 2)
        val cells = DateCalendarModel.monthCells(
            YearMonth.of(2026, 9),
            DayOfWeek.MONDAY,
            mapOf(assignedDate to "出遊清單"),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30),
        )

        assertEquals(35, cells.size)
        val assignedCell = cells.single { it.date == assignedDate }
        assertTrue(assignedCell.isAssigned)
        assertEquals("出遊清單", assignedCell.noteTitle)
    }

    @Test
    fun calendarStartsOnTheRequestedWeekdayAndHidesOutOfRangeDates() {
        val cells = DateCalendarModel.monthCells(
            YearMonth.of(2026, 9),
            DayOfWeek.MONDAY,
            emptyMap(),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30),
        )

        assertNull(cells.first().date)
        assertEquals(LocalDate.of(2026, 9, 1), cells[1].date)
        assertFalse(cells[1].isAssigned)
    }
}
