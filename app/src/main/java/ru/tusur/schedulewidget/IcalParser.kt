package ru.tusur.schedulewidget

import java.io.BufferedReader
import java.io.StringReader
import java.time.*
import java.time.format.DateTimeFormatter

object IcalParser {
    private val tomsk = ZoneId.of("Asia/Tomsk")

    // Unfold RFC 5545 continuation lines before parsing VEVENT properties.
    fun parse(text: String): List<Lesson> {
        val unfolded = mutableListOf<String>()
        text.replace("\r\n", "\n").replace("\r", "\n").split("\n").forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] += line.drop(1)
            } else unfolded += line
        }

        val events = mutableListOf<Lesson>()
        var event: MutableMap<String, String>? = null
        for (line in unfolded) {
            if (line == "BEGIN:VEVENT") { event = mutableMapOf(); continue }
            if (line == "END:VEVENT") {
                event?.let { map ->
                    val start = parseDate(map["DTSTART"] ?: return@let)
                    val end = parseDate(map["DTEND"] ?: return@let)
                    val title = map["SUMMARY"]?.unescapeIcs()?.trim().orEmpty()
                    if (start != null && end != null && title.isNotBlank()) {
                        events += Lesson(start.toEpochMilli(), end.toEpochMilli(), title,
                            map["LOCATION"]?.unescapeIcs()?.trim().orEmpty(),
                            map["X-ALT-DESC"]?.unescapeIcs()?.trim().orEmpty(),
                            map["DESCRIPTION"]?.unescapeIcs()?.trim().orEmpty())
                    }
                }
                event = null
                continue
            }
            val target = event ?: continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val keyPart = line.substring(0, colon)
            val key = keyPart.substringBefore(';').uppercase()
            if (key in setOf("DTSTART", "DTEND", "SUMMARY", "LOCATION", "DESCRIPTION", "X-ALT-DESC")) {
                target[key] = line.substring(colon + 1)
                if (key == "DTSTART" || key == "DTEND") target[key + "_PARAMS"] = keyPart
            }
        }
        return events.distinctBy { "${it.start}|${it.end}|${it.title}|${it.room}" }.sortedBy { it.start }
    }

    private fun parseDate(value: String): Instant? {
        return try {
            when {
                value.endsWith("Z") -> LocalDateTime.parse(value.removeSuffix("Z"),
                    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).toInstant(ZoneOffset.UTC)
                value.length == 8 -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay(tomsk).toInstant()
                else -> {
                    val normalized = when (value.length) {
                        13 -> value + "00"
                        else -> value
                    }
                    LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                        .atZone(tomsk).toInstant()
                }
            }
        } catch (_: Exception) { null }
    }

    private fun String.unescapeIcs(): String = replace("\\n", "\n")
        .replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
