package tw.local.memonote.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

internal object DateCalendarModel {
    data class Cell(
        val date: LocalDate?,
        val noteTitle: String?,
        val isAssigned: Boolean,
    )

    fun monthCells(
        month: YearMonth,
        firstDayOfWeek: DayOfWeek,
        assignments: Map<LocalDate, String?>,
        minimum: LocalDate,
        maximum: LocalDate,
    ): List<Cell> {
        require(!minimum.isAfter(maximum)) { "minimum must not follow maximum" }

        val first = month.atDay(1)
        val leadingDays = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cellCount = ((leadingDays + month.lengthOfMonth() + 6) / 7) * 7

        return List(cellCount) { index ->
            val candidate = first.minusDays(leadingDays.toLong()).plusDays(index.toLong())
            val date = candidate.takeIf { !it.isBefore(minimum) && !it.isAfter(maximum) }
            Cell(
                date = date,
                noteTitle = date?.let(assignments::get),
                isAssigned = date != null && assignments.containsKey(date),
            )
        }
    }
}
