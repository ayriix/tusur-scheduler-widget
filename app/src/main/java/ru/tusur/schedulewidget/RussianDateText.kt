package ru.tusur.schedulewidget

import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Russian date labels with explicit genitive month forms (11 ноября, 8 марта). */
object RussianDateText {
    private val locale = Locale.forLanguageTag("ru-RU")
    private val genitiveMonths = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    fun monthGenitive(date: LocalDate): String = genitiveMonths[date.monthValue - 1]

    fun mainDate(date: LocalDate): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        return "$weekday, ${date.dayOfMonth} ${monthGenitive(date)}"
    }

    fun widgetDate(date: LocalDate): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            .replace(".", "")
        return "${weekday.replaceFirstChar { it.titlecase(locale) }} · ${date.dayOfMonth} ${monthGenitive(date)}"
    }

    fun accessibleDateTime(epochMillis: Long, zone: ZoneId): String {
        val value = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return "${value.dayOfMonth} ${monthGenitive(value.toLocalDate())}, %02d:%02d".format(
            locale, value.hour, value.minute
        )
    }
}
