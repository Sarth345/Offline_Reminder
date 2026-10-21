package com.example.offlinereminder.domain.export

import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RFC 5545 standards-compatible iCalendar (.ics) serializer.
 * Supports line folding (75 octets), string escaping, timezones,
 * all-day dates, full recurrence rules, and recurring exceptions via RECURRENCE-ID.
 */
object PlanerizeIcsExporter {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))

    fun export(
        events: List<Event>,
        exceptions: List<OccurrenceException> = emptyList(),
        stampInstant: Instant = Instant.now()
    ): String {
        val lines = mutableListOf<String>()

        lines.add("BEGIN:VCALENDAR")
        lines.add("VERSION:2.0")
        lines.add("PRODID:-//Planerize//Planerize 1.1//EN")
        lines.add("CALSCALE:GREGORIAN")
        lines.add("METHOD:PUBLISH")

        val stampStr = UTC_FORMATTER.format(stampInstant)

        for (event in events) {
            lines.add("BEGIN:VEVENT")
            lines.add("UID:${event.id}@planerize.local")
            lines.add("DTSTAMP:$stampStr")

            // Title & Description
            lines.add("SUMMARY:${escapeIcs(event.title)}")
            if (event.description.isNotBlank()) {
                lines.add("DESCRIPTION:${escapeIcs(event.description)}")
            }

            // Location
            event.location?.let { loc ->
                val locText = listOfNotNull(
                    loc.placeName.ifBlank { null },
                    loc.addressText.ifBlank { null },
                    loc.building.ifBlank { null },
                    loc.room.ifBlank { null }
                ).joinToString(", ")
                if (locText.isNotBlank()) {
                    lines.add("LOCATION:${escapeIcs(locText)}")
                }
            }

            // Priority: RFC 5545 (1=highest/critical, 3=high, 5=medium/normal, 9=low)
            val priorityNum = when (event.priority) {
                Priority.CRITICAL -> 1
                Priority.HIGH -> 3
                Priority.NORMAL -> 5
                Priority.LOW -> 9
            }
            lines.add("PRIORITY:$priorityNum")
            lines.add("STATUS:CONFIRMED")

            // Temporal DTSTART & DTEND
            when (val t = event.temporal) {
                is EventDateTime.DateOnly -> {
                    lines.add("DTSTART;VALUE=DATE:${DATE_FORMATTER.format(t.startDate)}")
                    lines.add("DTEND;VALUE=DATE:${DATE_FORMATTER.format(t.startDate.plusDays(1))}")
                }
                is EventDateTime.DateRange -> {
                    lines.add("DTSTART;VALUE=DATE:${DATE_FORMATTER.format(t.startDate)}")
                    lines.add("DTEND;VALUE=DATE:${DATE_FORMATTER.format(t.endDate.plusDays(1))}")
                }
                is EventDateTime.DateTime -> {
                    val dtStr = formatDateTime(t.dateTime, t.zoneId)
                    lines.add("DTSTART$dtStr")
                    val endStr = formatDateTime(t.dateTime.plusHours(1), t.zoneId)
                    lines.add("DTEND$endStr")
                }
                is EventDateTime.DateTimeRange -> {
                    val dtStartStr = formatDateTime(t.startDateTime, t.zoneId)
                    val dtEndStr = formatDateTime(t.endDateTime, t.zoneId)
                    lines.add("DTSTART$dtStartStr")
                    lines.add("DTEND$dtEndStr")
                }
            }

            // Recurrence Rule
            event.recurrenceRule?.let { rule ->
                val rrule = buildRRule(rule)
                if (rrule.isNotBlank()) {
                    lines.add("RRULE:$rrule")
                }

                // EXDATE
                if (rule.excludedDates.isNotEmpty()) {
                    for (exDate in rule.excludedDates.sorted()) {
                        lines.add("EXDATE;VALUE=DATE:${DATE_FORMATTER.format(exDate)}")
                    }
                }
            }

            lines.add("END:VEVENT")

            // RFC 5545 RECURRENCE-ID: moved/modified occurrences
            val eventExceptions = exceptions.filter { it.eventId == event.id }
            for (ex in eventExceptions) {
                if (ex.status == OccurrenceStatus.RESCHEDULED && ex.rescheduledStartDateTime != null) {
                    lines.add("BEGIN:VEVENT")
                    lines.add("UID:${event.id}@planerize.local")
                    lines.add("DTSTAMP:$stampStr")
                    // RECURRENCE-ID identifies the ORIGINAL occurrence date
                    lines.add("RECURRENCE-ID;VALUE=DATE:${DATE_FORMATTER.format(ex.originalDate)}")
                    val zone = ex.rescheduledZoneId ?: ex.originalZoneId
                    lines.add("DTSTART${formatDateTime(ex.rescheduledStartDateTime, zone)}")
                    val endDt = ex.rescheduledEndDateTime ?: ex.rescheduledStartDateTime.plusHours(1)
                    lines.add("DTEND${formatDateTime(endDt, zone)}")
                    lines.add("SUMMARY:${escapeIcs(ex.titleOverride ?: event.title)}")
                    lines.add("END:VEVENT")
                } else if (ex.titleOverride != null) {
                    lines.add("BEGIN:VEVENT")
                    lines.add("UID:${event.id}@planerize.local")
                    lines.add("DTSTAMP:$stampStr")
                    lines.add("RECURRENCE-ID;VALUE=DATE:${DATE_FORMATTER.format(ex.originalDate)}")
                    val zone = ex.originalZoneId
                    val startDt = ex.rescheduledStartDateTime ?: ex.originalStartDateTime
                    lines.add("DTSTART${formatDateTime(startDt, zone)}")
                    val endDt = ex.rescheduledEndDateTime ?: startDt.plusHours(1)
                    lines.add("DTEND${formatDateTime(endDt, zone)}")
                    lines.add("SUMMARY:${escapeIcs(ex.titleOverride)}")
                    lines.add("END:VEVENT")
                }
            }
        }

        lines.add("END:VCALENDAR")

        return lines.joinToString("\r\n") { foldLine(it) } + "\r\n"
    }

    private fun formatDateTime(ldt: LocalDateTime, zoneId: ZoneId): String {
        return if (zoneId == ZoneId.of("UTC") || zoneId.id == "Z") {
            ":${DATE_TIME_FORMATTER.format(ldt)}Z"
        } else {
            ";TZID=${zoneId.id}:${DATE_TIME_FORMATTER.format(ldt)}"
        }
    }

    private fun buildRRule(rule: RecurrenceRule): String {
        val parts = mutableListOf<String>()
        parts.add("FREQ=${rule.frequency.name}")

        if (rule.interval > 1) {
            parts.add("INTERVAL=${rule.interval}")
        }

        if (rule.daysOfWeek.isNotEmpty()) {
            val days = rule.daysOfWeek.map { toIcsDay(it) }.joinToString(",")
            parts.add("BYDAY=$days")
        }

        if (rule.dayOfMonth != null) {
            parts.add("BYMONTHDAY=${rule.dayOfMonth}")
        }

        if (rule.monthOfYear != null) {
            parts.add("BYMONTH=${rule.monthOfYear}")
        }

        when (val end = rule.endCondition) {
            is RecurrenceEnd.Never -> { /* No UNTIL or COUNT */ }
            is RecurrenceEnd.Count -> parts.add("COUNT=${end.maxOccurrences}")
            is RecurrenceEnd.UntilDate -> {
                parts.add("UNTIL=${DATE_FORMATTER.format(end.date)}T235959Z")
            }
        }

        return parts.joinToString(";")
    }

    private fun toIcsDay(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }

    private fun escapeIcs(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
    }

    /**
     * Folds a line according to RFC 5545 section 3.1:
     * Lines of text SHOULD NOT be longer than 75 octets, excluding the line break.
     * Long lines are folded by inserting CRLF followed by a space.
     * Code points (including Unicode surrogate pairs such as Hindi conjuncts,
     * emojis, and accented characters) are preserved atomically without splitting.
     */
    fun foldLine(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) {
            return line
        }

        val sb = StringBuilder()
        var currentByteCount = 0

        val codePoints = line.codePoints().toArray()
        for (cp in codePoints) {
            val charStr = String(Character.toChars(cp))
            val charBytes = charStr.toByteArray(Charsets.UTF_8).size
            if (currentByteCount + charBytes > 75) {
                sb.append("\r\n ")
                currentByteCount = 1
            }
            sb.append(charStr)
            currentByteCount += charBytes
        }

        return sb.toString()
    }
}
