package com.example.offlinereminder.domain.quickadd

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.regex.Pattern

/**
 * Pure Kotlin deterministic parser for Quick Add input.
 * Extracts title, dates, times, recurrence, priority, category, groups, tags, and reminders
 * with zero network/cloud/LLM dependencies.
 */
class QuickAddParser(
    private val timeProvider: TimeProvider
) {

    fun parse(input: String): QuickAddDraft {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return QuickAddDraft(
                rawInput = input,
                title = "",
                validationErrors = listOf("Event title is required")
            )
        }

        var text = trimmed
        val ambiguities = mutableListOf<String>()
        val unparsedTokens = mutableListOf<String>()
        val validationErrors = mutableListOf<String>()

        // 1. Extract Priority (!critical, !high, !normal, !low, !p1..p4, or capture unknown !token)
        var priority = Priority.NORMAL
        val priorityMatch = EXCLAMATION_PATTERN.matcher(text)
        if (priorityMatch.find()) {
            val token = priorityMatch.group(1)?.lowercase(Locale.ROOT) ?: ""
            priority = when (token) {
                "critical", "p1" -> Priority.CRITICAL
                "high", "p2" -> Priority.HIGH
                "normal", "medium", "p3" -> Priority.NORMAL
                "low", "p4" -> Priority.LOW
                else -> {
                    unparsedTokens.add("!$token")
                    Priority.NORMAL
                }
            }
            text = text.removeRange(priorityMatch.start(), priorityMatch.end())
        }

        // 2. Extract Category (#Category or cat:Category)
        var categoryName: String? = null
        val catMatcher = CATEGORY_PATTERN.matcher(text)
        val catRanges = mutableListOf<IntRange>()
        while (catMatcher.find()) {
            val cat = catMatcher.group(1) ?: catMatcher.group(2)
            if (categoryName == null && !cat.isNullOrBlank()) {
                categoryName = cat
            }
            catRanges.add(catMatcher.start()..catMatcher.end())
        }
        for (range in catRanges.reversed()) {
            text = text.removeRange(range.first, range.last)
        }

        // 3. Extract Groups (@Group or grp:Group)
        val groupNames = mutableListOf<String>()
        val groupMatcher = GROUP_PATTERN.matcher(text)
        val groupRanges = mutableListOf<IntRange>()
        while (groupMatcher.find()) {
            val grp = groupMatcher.group(1) ?: groupMatcher.group(2)
            if (!grp.isNullOrBlank()) {
                groupNames.add(grp)
                groupRanges.add(groupMatcher.start()..groupMatcher.end())
            }
        }
        for (range in groupRanges.reversed()) {
            text = text.removeRange(range.first, range.last)
        }

        // 4. Extract Tags (+Tag or tag:Tag)
        val tagNames = mutableListOf<String>()
        val tagMatcher = TAG_PATTERN.matcher(text)
        val tagRanges = mutableListOf<IntRange>()
        while (tagMatcher.find()) {
            val tag = tagMatcher.group(1) ?: tagMatcher.group(2)
            if (!tag.isNullOrBlank()) {
                tagNames.add(tag)
                tagRanges.add(tagMatcher.start()..tagMatcher.end())
            }
        }
        for (range in tagRanges.reversed()) {
            text = text.removeRange(range.first, range.last)
        }

        // 5. Extract Reminder (rem:15m, rem:1h, rem:0m, rem:at-time)
        var reminderMinutes: Int? = null
        val remMatch = REMINDER_PATTERN.matcher(text)
        if (remMatch.find()) {
            val valueStr = remMatch.group(1)
            val unitStr = remMatch.group(2)?.lowercase(Locale.ROOT)
            val specialStr = remMatch.group(3)?.lowercase(Locale.ROOT)

            if (specialStr != null && (specialStr == "at-time" || specialStr == "attime" || specialStr == "0m")) {
                reminderMinutes = 0
            } else if (valueStr != null) {
                val value = valueStr.toIntOrNull()
                if (value != null) {
                    reminderMinutes = when (unitStr) {
                        "m", "min", "mins", "minute", "minutes" -> value
                        "h", "hr", "hrs", "hour", "hours" -> value * 60
                        "d", "day", "days" -> value * 1440
                        else -> value
                    }
                }
            }
            text = text.removeRange(remMatch.start(), remMatch.end())
        }

        // 6. Extract Recurrence: prioritize compound "every ..." over standalone words
        var recurrenceRule: RecurrenceRule? = null
        val compoundMatch = COMPOUND_RECURRENCE_PATTERN.matcher(text)
        if (compoundMatch.find()) {
            val matchStr = compoundMatch.group(0)?.trim()?.lowercase(Locale.ROOT) ?: ""
            recurrenceRule = parseRecurrence(matchStr)
            text = text.removeRange(compoundMatch.start(), compoundMatch.end())
        } else {
            val standaloneMatch = STANDALONE_RECURRENCE_PATTERN.matcher(text)
            if (standaloneMatch.find()) {
                val matchStr = standaloneMatch.group(0)?.trim()?.lowercase(Locale.ROOT) ?: ""
                recurrenceRule = parseRecurrence(matchStr)
                text = text.removeRange(standaloneMatch.start(), standaloneMatch.end())
            }
        }

        // 7. Extract Time: prioritize explicit numeric times (3pm, 14:00) before named times
        var parsedTime: LocalTime? = null
        val numericTimeMatch = NUMERIC_TIME_PATTERN.matcher(text)
        if (numericTimeMatch.find()) {
            val rawTime = numericTimeMatch.group(0)?.trim() ?: ""
            parsedTime = parseTime(rawTime)
            text = text.removeRange(numericTimeMatch.start(), numericTimeMatch.end())
        } else {
            val specialTimeMatch = SPECIAL_TIME_PATTERN.matcher(text)
            if (specialTimeMatch.find()) {
                val rawTime = specialTimeMatch.group(0)?.trim() ?: ""
                parsedTime = parseTime(rawTime)
                text = text.removeRange(specialTimeMatch.start(), specialTimeMatch.end())
            } else {
                val namedTimeMatch = NAMED_TIME_PATTERN.matcher(text)
                if (namedTimeMatch.find()) {
                    val rawTime = namedTimeMatch.group(0)?.trim() ?: ""
                    parsedTime = parseTime(rawTime)
                    text = text.removeRange(namedTimeMatch.start(), namedTimeMatch.end())
                }
            }
        }

        // 8. Extract Date (relative dates, ISO dates, month names, day of week)
        var parsedDate: LocalDate? = null
        val dateMatch = DATE_PATTERN.matcher(text)
        if (dateMatch.find()) {
            val dateStr = dateMatch.group(0)?.trim() ?: ""
            val parseResult = parseDate(dateStr, ambiguities)
            if (parseResult != null) {
                parsedDate = parseResult
                text = text.removeRange(dateMatch.start(), dateMatch.end())
            }
        }

        // If recurrence is weekly on a specific day but no start date given, align start date
        val today = timeProvider.nowLocalDate()
        val nowTime = timeProvider.nowLocalTime()

        if (recurrenceRule != null && parsedDate == null) {
            val targetDays = recurrenceRule.daysOfWeek
            if (targetDays.isNotEmpty()) {
                val nextDay = targetDays.first()
                parsedDate = today.with(TemporalAdjusters.nextOrSame(nextDay))
            } else {
                parsedDate = today
            }
        }

        // If time was specified without a date:
        // If time is before now, default to tomorrow; otherwise default to today.
        if (parsedTime != null && parsedDate == null) {
            parsedDate = if (parsedTime.isBefore(nowTime)) today.plusDays(1) else today
        }

        // 9. Clean up remaining title
        var title = text
            .replace(CLEANUP_REGEX, " ")
            .trim()
            // Strip leading "on ", "at ", "every " if left over as dangling prefixes/suffixes
            .replace(Regex("^(?:on|at|every)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:on|at|every)$", RegexOption.IGNORE_CASE), "")
            .trim()

        if (title.isBlank()) {
            validationErrors.add("Event title cannot be empty")
        }

        val isAllDay = (parsedTime == null)

        return QuickAddDraft(
            rawInput = input,
            title = title,
            date = parsedDate,
            time = parsedTime,
            isAllDay = isAllDay,
            recurrenceRule = recurrenceRule,
            priority = priority,
            categoryName = categoryName,
            groupNames = groupNames,
            tagNames = tagNames,
            reminderMinutesBefore = reminderMinutes,
            validationErrors = validationErrors,
            ambiguities = ambiguities,
            unparsedTokens = unparsedTokens
        )
    }

    private fun parseRecurrence(expression: String): RecurrenceRule? {
        val expr = expression.lowercase(Locale.ROOT).trim()
        return when {
            expr == "daily" || expr == "every day" -> {
                RecurrenceRule.daily()
            }
            expr == "weekly" || expr == "every week" -> {
                RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = 1)
            }
            expr == "monthly" || expr == "every month" -> {
                RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = 1)
            }
            expr == "yearly" || expr == "annually" || expr == "every year" -> {
                RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, interval = 1)
            }
            expr == "weekdays" || expr == "every weekday" -> {
                RecurrenceRule.weekdays()
            }
            expr == "weekends" || expr == "every weekend" -> {
                RecurrenceRule.weekends()
            }
            expr.startsWith("every ") -> {
                val remainder = expr.removePrefix("every ").trim()
                val dayOfWeek = parseDayOfWeek(remainder)
                if (dayOfWeek != null) {
                    RecurrenceRule.weekly(
                        daysOfWeek = setOf(dayOfWeek)
                    )
                } else {
                    // Check "every N days/weeks/months/years"
                    val intervalMatch = Regex("^(\\d+)\\s+(day|days|week|weeks|month|months|year|years)$").matchEntire(remainder)
                    if (intervalMatch != null) {
                        val interval = intervalMatch.groupValues[1].toIntOrNull() ?: 1
                        val unit = intervalMatch.groupValues[2]
                        when {
                            unit.startsWith("day") -> RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = interval)
                            unit.startsWith("week") -> RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = interval)
                            unit.startsWith("month") -> RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = interval)
                            unit.startsWith("year") -> RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, interval = interval)
                            else -> null
                        }
                    } else null
                }
            }
            else -> null
        }
    }

    private fun parseDayOfWeek(str: String): DayOfWeek? {
        val s = str.lowercase(Locale.ROOT).trim()
        return when (s) {
            "monday", "mon" -> DayOfWeek.MONDAY
            "tuesday", "tue" -> DayOfWeek.TUESDAY
            "wednesday", "wed" -> DayOfWeek.WEDNESDAY
            "thursday", "thu" -> DayOfWeek.THURSDAY
            "friday", "fri" -> DayOfWeek.FRIDAY
            "saturday", "sat" -> DayOfWeek.SATURDAY
            "sunday", "sun" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun parseTime(str: String): LocalTime? {
        var s = str.lowercase(Locale.ROOT).trim()
        if (s.startsWith("at ")) {
            s = s.removePrefix("at ").trim()
        }
        if (s.startsWith("in the ")) {
            s = s.removePrefix("in the ").trim()
        }

        return when (s) {
            "noon" -> LocalTime.of(12, 0)
            "midnight" -> LocalTime.of(0, 0)
            "morning" -> LocalTime.of(9, 0)
            "afternoon" -> LocalTime.of(14, 0)
            "evening" -> LocalTime.of(18, 0)
            "night" -> LocalTime.of(20, 0)
            else -> {
                // 12-hour: 3pm, 3:30pm, 3:30 am, 11am
                val amPmMatch = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$").matchEntire(s)
                if (amPmMatch != null) {
                    var hour = amPmMatch.groupValues[1].toInt()
                    val minute = amPmMatch.groupValues[2].ifEmpty { "0" }.toInt()
                    val isPm = amPmMatch.groupValues[3] == "pm"

                    if (hour == 12) {
                        hour = if (isPm) 12 else 0
                    } else if (isPm) {
                        hour += 12
                    }
                    if (hour in 0..23 && minute in 0..59) {
                        LocalTime.of(hour, minute)
                    } else null
                } else {
                    // 24-hour: 14:00, 09:30
                    val h24Match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(s)
                    if (h24Match != null) {
                        val hour = h24Match.groupValues[1].toInt()
                        val minute = h24Match.groupValues[2].toInt()
                        if (hour in 0..23 && minute in 0..59) {
                            LocalTime.of(hour, minute)
                        } else null
                    } else null
                }
            }
        }
    }

    private fun parseDate(str: String, ambiguities: MutableList<String>): LocalDate? {
        var s = str.lowercase(Locale.ROOT).trim()
        if (s.startsWith("on ")) {
            s = s.removePrefix("on ").trim()
        }

        val today = timeProvider.nowLocalDate()

        return when (s) {
            "today" -> today
            "tomorrow", "tmrw" -> today.plusDays(1)
            "day after tomorrow" -> today.plusDays(2)
            "yesterday" -> today.minusDays(1)
            else -> {
                // Check "this <day>" or "next <day>" or single day name
                if (s.startsWith("this ")) {
                    val day = parseDayOfWeek(s.removePrefix("this ").trim())
                    if (day != null) today.with(TemporalAdjusters.nextOrSame(day)) else null
                } else if (s.startsWith("next ")) {
                    val day = parseDayOfWeek(s.removePrefix("next ").trim())
                    if (day != null) today.with(TemporalAdjusters.next(day)).plusWeeks(if (today.dayOfWeek == day) 0 else 0) else null
                } else {
                    val day = parseDayOfWeek(s)
                    if (day != null) {
                        today.with(TemporalAdjusters.nextOrSame(day))
                    } else {
                        // Standard formats: YYYY-MM-DD or YYYY/MM/DD
                        val isoMatch = Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$").matchEntire(s)
                        if (isoMatch != null) {
                            val y = isoMatch.groupValues[1].toInt()
                            val m = isoMatch.groupValues[2].toInt()
                            val d = isoMatch.groupValues[3].toInt()
                            runCatching { LocalDate.of(y, m, d) }.getOrNull()
                        } else {
                            // MM-DD or MM/DD
                            val mdMatch = Regex("^(\\d{1,2})[-/](\\d{1,2})$").matchEntire(s)
                            if (mdMatch != null) {
                                val m = mdMatch.groupValues[1].toInt()
                                val d = mdMatch.groupValues[2].toInt()
                                ambiguities.add("Date '$s' interpreted as MM/DD. Specify YYYY-MM-DD for explicit format.")
                                val candidate = runCatching { LocalDate.of(today.year, m, d) }.getOrNull()
                                if (candidate != null && candidate.isBefore(today)) {
                                    candidate.plusYears(1)
                                } else candidate
                            } else {
                                // Month name dates: e.g. "Oct 25", "October 25", "25 Oct", "25th October"
                                parseNamedMonthDate(s, today)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseNamedMonthDate(s: String, today: LocalDate): LocalDate? {
        val clean = s.replace(Regex("(\\d+)(?:st|nd|rd|th)"), "$1")
        val parts = clean.split(Regex("\\s+"))
        if (parts.size == 2) {
            val monthFirst = parseMonth(parts[0])
            val dayFromSecond = parts[1].toIntOrNull()
            if (monthFirst != null && dayFromSecond != null && dayFromSecond in 1..31) {
                val candidate = runCatching { LocalDate.of(today.year, monthFirst, dayFromSecond) }.getOrNull()
                return if (candidate != null && candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }

            val dayFromFirst = parts[0].toIntOrNull()
            val monthSecond = parseMonth(parts[1])
            if (dayFromFirst != null && monthSecond != null && dayFromFirst in 1..31) {
                val candidate = runCatching { LocalDate.of(today.year, monthSecond, dayFromFirst) }.getOrNull()
                return if (candidate != null && candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }
        }
        return null
    }

    private fun parseMonth(str: String): Month? {
        return when (str.lowercase(Locale.ROOT)) {
            "jan", "january" -> Month.JANUARY
            "feb", "february" -> Month.FEBRUARY
            "mar", "march" -> Month.MARCH
            "apr", "april" -> Month.APRIL
            "may" -> Month.MAY
            "jun", "june" -> Month.JUNE
            "jul", "july" -> Month.JULY
            "aug", "august" -> Month.AUGUST
            "sep", "sept", "september" -> Month.SEPTEMBER
            "oct", "october" -> Month.OCTOBER
            "nov", "november" -> Month.NOVEMBER
            "dec", "december" -> Month.DECEMBER
            else -> null
        }
    }

    companion object {
        private val EXCLAMATION_PATTERN: Pattern = Pattern.compile(
            "!([a-zA-Z0-9_-]+)\\b",
            Pattern.CASE_INSENSITIVE
        )

        private val CATEGORY_PATTERN: Pattern = Pattern.compile(
            "(?:#([a-zA-Z0-9_-]+)|\\b(?:cat|category):([a-zA-Z0-9_-]+))\\b"
        )

        private val GROUP_PATTERN: Pattern = Pattern.compile(
            "(?:@([a-zA-Z0-9_-]+)|\\b(?:group|grp):([a-zA-Z0-9_-]+))\\b"
        )

        private val TAG_PATTERN: Pattern = Pattern.compile(
            "(?:\\+([a-zA-Z0-9_-]+)|\\btag:([a-zA-Z0-9_-]+))\\b"
        )

        private val REMINDER_PATTERN: Pattern = Pattern.compile(
            "\\b(?:rem|reminder):(?:(\\d+)(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)?|(at-time|attime|0m))\\b",
            Pattern.CASE_INSENSITIVE
        )

        private val COMPOUND_RECURRENCE_PATTERN: Pattern = Pattern.compile(
            "\\b(?:every\\s+(?:\\d+\\s+)?(?:day|days|week|weeks|month|months|year|years|weekday|weekdays|weekend|weekends|monday|mon|tuesday|tue|wednesday|wed|thursday|thu|friday|fri|saturday|sat|sunday|sun))\\b",
            Pattern.CASE_INSENSITIVE
        )

        private val STANDALONE_RECURRENCE_PATTERN: Pattern = Pattern.compile(
            "\\b(?:daily|weekly|monthly|yearly|annually|weekdays|weekends)\\b",
            Pattern.CASE_INSENSITIVE
        )

        private val NUMERIC_TIME_PATTERN: Pattern = Pattern.compile(
            "\\b(?:at\\s+)?(?:\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)|\\d{1,2}:\\d{2})\\b",
            Pattern.CASE_INSENSITIVE
        )

        private val SPECIAL_TIME_PATTERN: Pattern = Pattern.compile(
            "\\b(?:at\\s+)?(?:noon|midnight)\\b",
            Pattern.CASE_INSENSITIVE
        )

        private val NAMED_TIME_PATTERN: Pattern = Pattern.compile(
            "\\b(?:in the\\s+|at\\s+)?(?:morning|afternoon|evening|night)\\b",
            Pattern.CASE_INSENSITIVE
        )

        private val DATE_PATTERN: Pattern = Pattern.compile(
            "\\b(?:on\\s+)?(?:today|tomorrow|tmrw|day\\s+after\\s+tomorrow|yesterday|(?:this\\s+|next\\s+)?(?:monday|mon|tuesday|tue|wednesday|wed|thursday|thu|friday|fri|saturday|sat|sunday|sun)|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}|(?:jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\\s+\\d{1,2}(?:st|nd|rd|th)?|\\d{1,2}(?:st|nd|rd|th)?\\s+(?:jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december))\\b",
            Pattern.CASE_INSENSITIVE
        )

        private val CLEANUP_REGEX = Regex("\\s{2,}")
    }
}
