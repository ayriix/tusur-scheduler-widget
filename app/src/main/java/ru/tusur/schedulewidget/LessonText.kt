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

    /** Compact label for narrow widget cards while preserving the building prefix. */
    fun compactRoom(raw: String): String {
        if (raw.isBlank()) return "каб. —"

        val roomPattern = Regex(
            "(?i)^\\s*([\\p{L}][\\p{L}.-]*)?\\s*(\\d+[\\p{L}]?)\\s*(?:кабинет|каб\\.?)?\\s*$"
        )
        val roomsByBuilding = linkedMapOf<String, MutableList<String>>()
        var currentBuilding = ""

        // Locations can contain several room numbers separated by commas, semicolons or dots.
        // If the feed specifies a building only once, following bare room numbers inherit it.
        raw.replace('\n', ',')
            .split(Regex("\\s*[,;·]\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { part ->
                val buildingOnly = Regex("(?i)^([\\p{L}][\\p{L}.-]*)$").matchEntire(part)
                if (buildingOnly != null) {
                    val suppliedBuilding = buildingOnly.groupValues[1].trim().trimEnd('.')
                    currentBuilding = when (suppliedBuilding.uppercase()) {
                        "Ф", "ФЭТ" -> "ФЭТ"
                        else -> suppliedBuilding.uppercase()
                    }
                    return@forEach
                }

                val match = roomPattern.matchEntire(part) ?: return@forEach
                val suppliedBuilding = match.groupValues[1].trim().trimEnd('.')
                if (suppliedBuilding.isNotBlank()) {
                    currentBuilding = when (suppliedBuilding.uppercase()) {
                        "Ф", "ФЭТ" -> "ФЭТ"
                        else -> suppliedBuilding.uppercase()
                    }
                }
                val roomNumber = match.groupValues[2]
                val buildingRooms = roomsByBuilding.getOrPut(currentBuilding) { mutableListOf() }
                if (roomNumber !in buildingRooms) buildingRooms += roomNumber
            }

        if (roomsByBuilding.isEmpty()) {
            // Unknown location formats are kept intact instead of stripping useful context.
            return room(raw)
        }

        return roomsByBuilding.entries.joinToString(" · ") { (building, numbers) ->
            val roomNumbers = numbers.joinToString("; ")
            if (building.isBlank()) "$roomNumbers каб." else "$building $roomNumbers каб."
        }
    }

}
