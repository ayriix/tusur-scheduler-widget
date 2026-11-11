package ru.tusur.schedulewidget

data class Lesson(
    val start: Long,
    val end: Long,
    val title: String,
    val room: String,
    val teacher: String,
    val note: String = "",
    val type: String = "other"
)
