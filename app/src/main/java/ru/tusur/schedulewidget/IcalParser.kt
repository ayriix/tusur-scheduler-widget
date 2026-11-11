package ru.tusur.schedulewidget

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object IcalParser {
    private val defaultZone = ZoneId.of("Asia/Tomsk")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT)

    /** Parses the RFC 5545 event subset used by the public TUSUR timetable feed. */
    fun parse(text: String): List<Lesson> {
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.lastIndex] += line.drop(1)
            } else {
                lines += line
            }
        }

        val lessons = mutableListOf<Lesson>()
        var event: MutableMap<String, String>? = null
        for (line in lines) {
            when (line) {
                "BEGIN:VEVENT" -> {
                    event = mutableMapOf()
                    continue
                }
                "END:VEVENT" -> {
                    event?.let { fields ->
                        val start = parseDate(fields["DTSTART"], fields["DTSTART_PARAMS"])
                        val end = parseDate(fields["DTEND"], fields["DTEND_PARAMS"])
                        val title = fields["SUMMARY"].orEmpty().unescapeIcs().trim()
                        if (start != null && end != null && end > start && title.isNotBlank()) {
                            lessons += Lesson(
                                start = start.toEpochMilli(),
                                end = end.toEpochMilli(),
                                title = title,
                                room = fields["LOCATION"].orEmpty().unescapeIcs().replace('\n', ' ').trim(),
                                teacher = fields["X-ALT-DESC"].orEmpty().unescapeIcs().trim(),
                                note = fields["DESCRIPTION"].orEmpty().unescapeIcs().trim(),
                                type = detectLessonType(listOf(
                                    fields["SUMMARY"], fields["DESCRIPTION"], fields["X-ALT-DESC"],
                                    fields["CATEGORIES"], fields["COMMENT"], fields["CLASS"]
                                ).filterNotNull().joinToString(" ").unescapeIcs())
                            )
                        }
                    }
                    event = null
                    continue
                }
            }

            val target = event ?: continue
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val property = line.substring(0, separator)
            val key = property.substringBefore(';').uppercase(Locale.ROOT)
            if (key in setOf("DTSTART", "DTEND", "SUMMARY", "LOCATION", "DESCRIPTION", "X-ALT-DESC", "CATEGORIES", "COMMENT", "CLASS")) {
                target[key] = line.substring(separator + 1)
                target["${key}_PARAMS"] = property
            }
        }

        return lessons.distinctBy { "${it.start}|${it.end}|${it.title}|${it.room}" }.sortedBy { it.start }
    }

    private fun detectLessonType(source: String): String {
        val text = source.lowercase(Locale.ROOT)
        return when {
            Regex("лабораторн|\\blab\\b").containsMatchIn(text) -> "laboratory"
            Regex("лекци|\\blecture\\b").containsMatchIn(text) -> "lecture"
            Regex("семинар|\\bseminar\\b").containsMatchIn(text) -> "seminar"
            Regex("практическ|\\bpractice\\b").containsMatchIn(text) -> "practice"
            Regex("консультац|\\bconsultation\\b").containsMatchIn(text) -> "consultation"
            Regex("экзамен|\\bexam\\b").containsMatchIn(text) -> "exam"
            else -> "other"
        }
    }

    private fun parseDate(value: String?, parameters: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            when {
                value.endsWith("Z") -> LocalDateTime.parse(value.removeSuffix("Z"), dateTimeFormatter)
                    .toInstant(ZoneOffset.UTC)
                value.length == 8 -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay(defaultZone).toInstant()
                else -> {
                    val zoneId = Regex("TZID=([^;:]+)", RegexOption.IGNORE_CASE)
                        .find(parameters.orEmpty())?.groupValues?.getOrNull(1)
                    val zone = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone
                    LocalDateTime.parse(value, dateTimeFormatter).atZone(zone).toInstant()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun String.unescapeIcs(): String = replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
}
