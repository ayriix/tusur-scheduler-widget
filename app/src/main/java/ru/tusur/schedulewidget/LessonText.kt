package ru.tusur.schedulewidget

object LessonText {
    /** Normalizes TUSUR room notation for a readable student-facing label. */
    fun room(raw: String): String {
        if (raw.isBlank()) return "Аудитория не указана"
        val parts = raw.replace('\n', ',').split(Regex("\\s*[,;]\\s*"))
            .map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return "Аудитория не указана"
        return parts.joinToString(" · ") { part ->
            val match = Regex("(?i)^([\\p{L}]+\\.?)\\s*(\\d+[\\p{L}]?)$").matchEntire(part)
            if (match != null) {
                val rawPrefix = match.groupValues[1].trimEnd('.')
                val number = match.groupValues[2]
                val prefix = if (rawPrefix.equals("ф", ignoreCase = true) || rawPrefix.equals("фэт", ignoreCase = true)) {
                    "ФЭТ"
                } else rawPrefix.uppercase()
                "$prefix $number кабинет"
            } else {
                val numberOnly = Regex("^\\d+[\\p{L}]?$").matchEntire(part)
                if (numberOnly != null) "${part} кабинет" else part
            }
        }
    }
}
