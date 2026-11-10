package ru.tusur.schedulewidget

data class GroupOption(
    val name: String,
    val faculty: String,
    val path: String
) {
    val browserUrl: String get() = "https://timetable.tusur.ru$path"
    val calendarUrl: String get() = "$browserUrl.ics"
    val label: String get() = "$name · $faculty"
}
