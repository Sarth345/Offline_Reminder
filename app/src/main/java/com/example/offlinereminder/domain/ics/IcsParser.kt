package com.example.offlinereminder.domain.ics

import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.LocationData
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class IcsOccurrenceException(
    val masterUid: String,
    val recurrenceOriginalDate: LocalDate,
    val newTemporal: EventDateTime?,
    val title: String?,
    val isCancelled: Boolean = false
)

data class IcsCalendarPreview(
    val candidateEvents: List<Event>,
    val candidateExceptions: List<IcsOccurrenceException> = emptyList(),
    val totalCount: Int,
    val recurringCount: Int,
    val warnings: List<String>
)

object IcsParser {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(
        icsContent: String,
        defaultCategoryId: String,
        defaultEventTypeId: String,
        nowInstant: Instant = Instant.now()
    ): IcsCalendarPreview {
        val warnings = mutableListOf<String>()
        val events = mutableListOf<Event>()
        val exceptions = mutableListOf<IcsOccurrenceException>()

        val rawLines = icsContent.lines()
        val unfoldedLines = unfoldLines(rawLines)

        var insideVEvent = false
        val currentEventProperties = mutableMapOf<String, String>()
        val currentExDates = mutableListOf<LocalDate>()

        for (line in unfoldedLines) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VEVENT", ignoreCase = true)) {
                insideVEvent = true
                currentEventProperties.clear()
                currentExDates.clear()
            } else if (trimmed.equals("END:VEVENT", ignoreCase = true)) {
                if (insideVEvent) {
                    val recurrenceIdKey = currentEventProperties.keys.firstOrNull { it.startsWith("RECURRENCE-ID") }
                    if (recurrenceIdKey != null) {
                        val recurrenceIdVal = currentEventProperties[recurrenceIdKey].orEmpty()
                        val origDate = parseExDate(recurrenceIdVal)
                        val uidKey = currentEventProperties.keys.firstOrNull { it.startsWith("UID") }
                        val uid = uidKey?.let { currentEventProperties[it] }.orEmpty().removeSuffix("@planerize.local")
                        val summaryKey = currentEventProperties.keys.firstOrNull { it.startsWith("SUMMARY") }
                        val summary = summaryKey?.let { unescapeIcs(currentEventProperties[it].orEmpty()) }

                        val dtStartKey = currentEventProperties.keys.firstOrNull { it.startsWith("DTSTART") }
                        val dtStartVal = dtStartKey?.let { currentEventProperties[it] }
                        val dtEndKey = currentEventProperties.keys.firstOrNull { it.startsWith("DTEND") }
                        val dtEndVal = dtEndKey?.let { currentEventProperties[it] }

                        val movedTemporal = if (dtStartKey != null && dtStartVal != null) {
                            parseTemporal(dtStartKey, dtStartVal, dtEndKey, dtEndVal, warnings, summary ?: "Exception")
                        } else null

                        if (origDate != null) {
                            // RECURRENCE-ID represents the ORIGINAL occurrence identity!
                            // We explicitly preserve this original identity rather than mapping to DTSTART.
                            exceptions.add(
                                IcsOccurrenceException(
                                    masterUid = uid,
                                    recurrenceOriginalDate = origDate,
                                    newTemporal = movedTemporal,
                                    title = summary
                                )
                            )
                        }
                    } else {
                        val event = buildEventFromProperties(
                            props = currentEventProperties,
                            exDates = currentExDates,
                            defaultCategoryId = defaultCategoryId,
                            defaultEventTypeId = defaultEventTypeId,
                            nowInstant = nowInstant,
                            warnings = warnings
                        )
                        if (event != null) {
                            events.add(event)
                        }
                    }
                    insideVEvent = false
                }
            } else if (insideVEvent) {
                val colonIdx = trimmed.indexOf(':')
                if (colonIdx > 0) {
                    val keyPart = trimmed.substring(0, colonIdx)
                    val valuePart = trimmed.substring(colonIdx + 1)

                    if (keyPart.startsWith("EXDATE", ignoreCase = true)) {
                        parseExDate(valuePart)?.let { currentExDates.add(it) }
                    } else {
                        // Store full key including parameters (e.g. DTSTART;TZID=America/New_York)
                        currentEventProperties[keyPart.uppercase()] = valuePart
                    }
                }
            }
        }

        val recurringCount = events.count { it.isRecurring }
        return IcsCalendarPreview(
            candidateEvents = events,
            candidateExceptions = exceptions,
            totalCount = events.size + exceptions.size,
            recurringCount = recurringCount,
            warnings = warnings
        )
    }

    private fun unfoldLines(rawLines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var current: StringBuilder? = null

        for (line in rawLines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current?.append(line.substring(1))
            } else {
                current?.let { result.add(it.toString()) }
                current = StringBuilder(line)
            }
        }
        current?.let { result.add(it.toString()) }
        return result
    }

    private fun buildEventFromProperties(
        props: Map<String, String>,
        exDates: List<LocalDate>,
        defaultCategoryId: String,
        defaultEventTypeId: String,
        nowInstant: Instant,
        warnings: MutableList<String>
    ): Event? {
        val summaryKey = props.keys.firstOrNull { it.startsWith("SUMMARY") }
        val summary = summaryKey?.let { unescapeIcs(props[it].orEmpty()) } ?: "Untitled Event"

        val descKey = props.keys.firstOrNull { it.startsWith("DESCRIPTION") }
        val description = descKey?.let { unescapeIcs(props[it].orEmpty()) } ?: ""

        val locKey = props.keys.firstOrNull { it.startsWith("LOCATION") }
        val locationStr = locKey?.let { unescapeIcs(props[it].orEmpty()) }

        val priorityKey = props.keys.firstOrNull { it.startsWith("PRIORITY") }
        val priority = priorityKey?.let { parsePriority(props[it].orEmpty()) } ?: Priority.NORMAL

        // Find DTSTART
        val dtStartKey = props.keys.firstOrNull { it.startsWith("DTSTART") }
        if (dtStartKey == null) {
            warnings.add("Event '$summary' skipped: missing DTSTART")
            return null
        }
        val dtStartVal = props[dtStartKey].orEmpty()
        val dtEndKey = props.keys.firstOrNull { it.startsWith("DTEND") }
        val dtEndVal = dtEndKey?.let { props[it].orEmpty() }

        val temporal = parseTemporal(dtStartKey, dtStartVal, dtEndKey, dtEndVal, warnings, summary)
            ?: return null

        // Parse RRULE
        val rruleKey = props.keys.firstOrNull { it.startsWith("RRULE") }
        val recurrenceRule = rruleKey?.let { parseRRule(props[it].orEmpty(), exDates, warnings, summary) }

        val location = locationStr?.let {
            LocationData(placeName = it)
        }

        return Event(
            id = UUID.randomUUID().toString(),
            title = summary,
            description = description,
            categoryId = defaultCategoryId,
            eventTypeId = defaultEventTypeId,
            priority = priority,
            temporal = temporal,
            recurrenceRule = recurrenceRule,
            location = location,
            createdAt = nowInstant,
            updatedAt = nowInstant
        )
    }

    private fun parseTemporal(
        startKey: String,
        startVal: String,
        endKey: String?,
        endVal: String?,
        warnings: MutableList<String>,
        summary: String
    ): EventDateTime? {
        val isDateOnly = startKey.contains("VALUE=DATE", ignoreCase = true) || startVal.length == 8

        if (isDateOnly) {
            val startDate = runCatching { LocalDate.parse(startVal.take(8), DATE_FORMATTER) }.getOrNull()
            if (startDate == null) {
                warnings.add("Event '$summary' has invalid DTSTART date: $startVal")
                return null
            }

            if (endVal != null && endVal.length >= 8) {
                val endDateRaw = runCatching { LocalDate.parse(endVal.take(8), DATE_FORMATTER) }.getOrNull()
                // In RFC 5545, DTEND is exclusive for all-day events. So a 1-day event on 2026-11-15 has DTEND 2026-11-16.
                if (endDateRaw != null) {
                    val inclusiveEndDate = endDateRaw.minusDays(1)
                    return if (inclusiveEndDate.isAfter(startDate)) {
                        EventDateTime.DateRange(startDate, inclusiveEndDate)
                    } else {
                        EventDateTime.DateOnly(startDate)
                    }
                }
            }
            return EventDateTime.DateOnly(startDate)
        } else {
            // DateTime or DateTimeRange
            val (startLdt, startZone) = parseDateTimeAndZone(startKey, startVal) ?: run {
                warnings.add("Event '$summary' has invalid DTSTART: $startVal")
                return null
            }

            if (endVal != null) {
                val (endLdt, _) = parseDateTimeAndZone(endKey ?: "", endVal) ?: (null to null)
                if (endLdt != null && endLdt.isAfter(startLdt)) {
                    return EventDateTime.DateTimeRange(startLdt, endLdt, startZone)
                }
            }
            return EventDateTime.DateTime(startLdt, startZone)
        }
    }

    private fun parseDateTimeAndZone(key: String, value: String): Pair<LocalDateTime, ZoneId>? {
        val zoneId = if (value.endsWith("Z", ignoreCase = true)) {
            ZoneId.of("UTC")
        } else if (key.contains("TZID=")) {
            val tzidPart = key.substringAfter("TZID=").substringBefore(';').substringBefore(':')
            runCatching { ZoneId.of(tzidPart) }.getOrDefault(ZoneId.systemDefault())
        } else {
            ZoneId.systemDefault()
        }

        val cleanVal = value.replace("Z", "")
        val ldt = runCatching {
            if (cleanVal.length >= 15) {
                LocalDateTime.parse(cleanVal.take(15), DATE_TIME_FORMATTER)
            } else if (cleanVal.length == 8) {
                LocalDate.parse(cleanVal, DATE_FORMATTER).atStartOfDay()
            } else {
                null
            }
        }.getOrNull() ?: return null

        return ldt to zoneId
    }

    private fun parseRRule(
        rruleStr: String,
        exDates: List<LocalDate>,
        warnings: MutableList<String>,
        summary: String
    ): RecurrenceRule? {
        val tokens = rruleStr.split(';').associate { part ->
            val pair = part.split('=')
            (pair.getOrNull(0)?.trim()?.uppercase() ?: "") to (pair.getOrNull(1)?.trim() ?: "")
        }

        val freqStr = tokens["FREQ"] ?: return null
        val frequency = runCatching { RecurrenceFrequency.valueOf(freqStr.uppercase()) }.getOrNull() ?: run {
            warnings.add("Event '$summary': unsupported FREQ '$freqStr'")
            return null
        }

        val interval = tokens["INTERVAL"]?.toIntOrNull() ?: 1

        val byDay = tokens["BYDAY"]?.split(',')?.mapNotNull { toDayOfWeek(it) }?.toSet() ?: emptySet()
        val byMonthDay = tokens["BYMONTHDAY"]?.toIntOrNull()
        val byMonth = tokens["BYMONTH"]?.toIntOrNull()

        val endCondition = if (tokens.containsKey("COUNT")) {
            val count = tokens["COUNT"]?.toIntOrNull() ?: 1
            RecurrenceEnd.Count(count)
        } else if (tokens.containsKey("UNTIL")) {
            val untilStr = tokens["UNTIL"].orEmpty()
            val untilDate = runCatching { LocalDate.parse(untilStr.take(8), DATE_FORMATTER) }.getOrNull()
            if (untilDate != null) RecurrenceEnd.UntilDate(untilDate) else RecurrenceEnd.Never
        } else {
            RecurrenceEnd.Never
        }

        return RecurrenceRule(
            frequency = frequency,
            interval = interval,
            daysOfWeek = byDay,
            dayOfMonth = byMonthDay,
            monthOfYear = byMonth,
            endCondition = endCondition,
            excludedDates = exDates.toSet()
        )
    }

    private fun toDayOfWeek(str: String): DayOfWeek? = when (str.trim().uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun parseExDate(value: String): LocalDate? {
        val clean = value.replace("Z", "").take(8)
        return runCatching { LocalDate.parse(clean, DATE_FORMATTER) }.getOrNull()
    }

    private fun parsePriority(valStr: String): Priority {
        val num = valStr.toIntOrNull() ?: return Priority.NORMAL
        return when (num) {
            in 1..2 -> Priority.CRITICAL
            in 3..4 -> Priority.HIGH
            in 5..6 -> Priority.NORMAL
            else -> Priority.LOW
        }
    }

    private fun unescapeIcs(text: String): String {
        return text
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }
}
