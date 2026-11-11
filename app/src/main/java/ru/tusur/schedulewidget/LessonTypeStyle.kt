package ru.tusur.schedulewidget

/** Fixed timetable category colours. These are deliberately independent of widget theme settings. */
object LessonTypeStyle {
    fun color(type: String): Int = when (type.lowercase()) {
        "lecture" -> 0xFF9AD97A.toInt()
        "laboratory" -> 0xFFB7A7F2.toInt()
        "practice" -> 0xFF79D4E8.toInt()
        "seminar" -> 0xFFF0CB70.toInt()
        "consultation" -> 0xFFE6A6D5.toInt()
        "exam" -> 0xFFE99A8E.toInt()
        else -> 0xFF64748B.toInt()
    }
}
