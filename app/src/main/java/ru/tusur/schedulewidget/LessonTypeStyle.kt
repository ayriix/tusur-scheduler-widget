package ru.tusur.schedulewidget

import java.util.Locale

/**
 * Fixed TUSUR lesson-type border colours. These colours are deliberately independent
 * from the user's configurable widget background and lesson fill colours.
 */
object LessonTypeStyle {
    private const val OTHER = "other"

    private fun resolvedType(type: String?, details: String? = null): String {
        // Older cached events may already contain "other" because earlier parser versions
        // did not recognize the exact TUSUR label. Re-check event details for those entries.
        val normalizedType = normalize(type)
        return if (normalizedType == OTHER) normalize(details) else normalizedType
    }

    fun isDashed(type: String?, details: String? = null): Boolean =
        resolvedType(type, details) == "independent_work"

    fun color(type: String?, details: String? = null): Int {
        val normalized = resolvedType(type, details)

        return when (normalized) {
            "lecture" -> 0xFF9AD97A.toInt()
            "laboratory" -> 0xFFB7A7F2.toInt()
            "practice" -> 0xFF79D4E8.toInt()
            "seminar" -> 0xFFF0CB70.toInt()
            "consultation" -> 0xFFE6A6D5.toInt()
            "exam" -> 0xFFE99A8E.toInt()
            "credit" -> 0xFFE8B96F.toInt()
            "graded_credit" -> 0xFFF0A66E.toInt()
            "course_work" -> 0xFFB59BEA.toInt()
            "course_project" -> 0xFFE3A0C5.toInt()
            "independent_work" -> 0xFF9BA9BA.toInt()
            else -> 0xFF64748B.toInt()
        }
    }

    /** Accepts canonical values, Russian TUSUR labels and the descriptive text in iCalendar events. */
    fun normalize(rawType: String?): String {
        val text = rawType.orEmpty()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace('\u00a0', ' ')
            .replace('–', '-')
            .replace('—', '-')
            .trim()

        if (text.isBlank()) return OTHER

        when (text) {
            "lecture" -> return "lecture"
            "laboratory", "lab" -> return "laboratory"
            "practice", "practical" -> return "practice"
            "seminar" -> return "seminar"
            "consultation" -> return "consultation"
            "exam" -> return "exam"
            "credit" -> return "credit"
            "graded_credit" -> return "graded_credit"
            "course_work" -> return "course_work"
            "course_project" -> return "course_project"
            "independent_work" -> return "independent_work"
            OTHER -> return OTHER
        }

        // TUSUR's iCalendar description commonly contains a dedicated "Вид занятия:" field.
        // Prefer it over subject names: a subject may contain words like "практикум" or
        // "лабораторный", even when the actual scheduled lesson is a different type.
        val explicitType = Regex(
            "(?:вид|тип)\\s+занятия\\s*[:：]\\s*(.+?)(?=\\s+(?:дата|время|место|преподаватель|группы)\\s+проведения\\b|[\\r\\n]|$)"
        ).find(text)?.groupValues?.getOrNull(1)
        if (!explicitType.isNullOrBlank()) {
            val explicit = normalizeLabel(explicitType)
            if (explicit != OTHER) return explicit
        }

        // If the explicit field is absent, use the last recognized category in the event text.
        // In TUSUR event summaries the lesson type is appended after the subject title.
        val patterns = listOf(
            "laboratory" to Regex("(?<![\\p{L}])(?:лаборатор\\p{L}*|лаб\\.?|laboratory|lab)(?![\\p{L}])"),
            "lecture" to Regex("(?<![\\p{L}])(?:лекци\\p{L}*|лек\\.?|lecture)(?![\\p{L}])"),
            "practice" to Regex("(?<![\\p{L}])(?:практическ\\p{L}*|практик(?:а|и|е|ой|у|ам|ах)|практ\\.?|practice|practical)(?![\\p{L}])"),
            "seminar" to Regex("(?<![\\p{L}])(?:семинар\\p{L}*|seminar)(?![\\p{L}])"),
            "consultation" to Regex("(?<![\\p{L}])(?:консультац\\p{L}*|конс\\.?|consultation)(?![\\p{L}])"),
            "exam" to Regex("(?<![\\p{L}])(?:кандидатск\\p{L}*\\s+)?экзамен\\p{L}*|(?<![\\p{L}])exam(?![\\p{L}])"),
            "graded_credit" to Regex("(?<![\\p{L}])(?:дифференцированн\\p{L}*\\s+зачет\\p{L}*|диф\\.?\\s*зачет\\p{L}*|зачет\\s+с\\s+оценкой)(?![\\p{L}])"),
            "credit" to Regex("(?<![\\p{L}])зачет\\p{L}*(?![\\p{L}])|(?<![\\p{L}])credit(?![\\p{L}])"),
            "course_project" to Regex("курсов\\p{L}*\\s+(?:проект\\p{L}*|проектирован\\p{L}*)"),
            "course_work" to Regex("курсов\\p{L}*\\s+работ\\p{L}*"),
            "independent_work" to Regex("самостоятельн\\p{L}*\\s+работ\\p{L}*")
        )

        val matches = patterns.flatMap { (category, regex) ->
            regex.findAll(text).map { category to it }.toList()
        }
        return matches.maxWithOrNull(
            compareBy<Pair<String, MatchResult>> { it.second.range.last }
                .thenBy { it.second.value.length }
        )?.first ?: normalizeLabel(text)
    }

    private fun normalizeLabel(raw: String): String {
        val value = raw.lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace('–', '-')
            .replace('—', '-')
            .trim()

        return when {
            value == "lecture" || value.contains("лекци") || Regex("(?<![\\p{L}])лек\\.?$").containsMatchIn(value) -> "lecture"
            value == "laboratory" || value == "lab" || value.contains("лаборатор") || Regex("(?<![\\p{L}])лаб\\.?$").containsMatchIn(value) -> "laboratory"
            value == "practice" || value == "practical" || value.contains("практическ") ||
                Regex("(?<![\\p{L}])практик(?:а|и|е|ой|у|ам|ах)(?![\\p{L}])").containsMatchIn(value) -> "practice"
            value == "seminar" || value.contains("семинар") -> "seminar"
            value == "consultation" || value.contains("консультац") || Regex("(?<![\\p{L}])конс\\.?$").containsMatchIn(value) -> "consultation"
            value == "exam" || value.contains("экзамен") -> "exam"
            value == "graded_credit" || value.contains("дифференцированн") || value.contains("диф. зачет") || value.contains("зачет с оценкой") -> "graded_credit"
            value == "credit" || value.contains("зачет") -> "credit"
            value == "course_project" || (value.contains("курсов") && (value.contains("проект") || value.contains("проектирован"))) -> "course_project"
            value == "course_work" || (value.contains("курсов") && value.contains("работ")) -> "course_work"
            value == "independent_work" || (value.contains("самостоятельн") && value.contains("работ")) -> "independent_work"
            else -> OTHER
        }
    }
}
