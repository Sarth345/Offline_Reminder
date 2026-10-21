package com.example.offlinereminder.domain.quickadd

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.model.Tag
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CreateEventFromQuickAddUseCaseTest {

    private val zone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-21T10:00:00Z"),
        currentZoneId = zone
    )

    private var eventRepository = InMemoryEventRepository()
    private var categoryRepository = InMemoryCategoryRepository()
    private var eventTypeRepository = InMemoryEventTypeRepository()
    private var eventGroupRepository = InMemoryEventGroupRepository()
    private var tagRepository = InMemoryTagRepository()

    private lateinit var createEventUseCase: CreateEventUseCase
    private lateinit var createEventFromQuickAddUseCase: CreateEventFromQuickAddUseCase

    @Before
    fun setUp() = runTest {
        eventRepository = InMemoryEventRepository()
        categoryRepository = InMemoryCategoryRepository()
        eventTypeRepository = InMemoryEventTypeRepository()
        eventGroupRepository = InMemoryEventGroupRepository()
        tagRepository = InMemoryTagRepository()

        // Seed default category and event type
        categoryRepository.saveCategory(Category(id = "cat_work", name = "Work", color = 0xFF4285F4.toInt()))
        eventTypeRepository.saveEventType(
            EventType(
                id = "type_task",
                name = "Task",
                key = "task",
                categoryId = "cat_work",
                createdAt = timeProvider.nowInstant()
            )
        )

        createEventUseCase = CreateEventUseCase(
            eventRepository = eventRepository,
            timeProvider = timeProvider
        )

        createEventFromQuickAddUseCase = CreateEventFromQuickAddUseCase(
            createEventUseCase = createEventUseCase,
            categoryRepository = categoryRepository,
            eventTypeRepository = eventTypeRepository,
            eventGroupRepository = eventGroupRepository,
            tagRepository = tagRepository,
            timeProvider = timeProvider
        )
    }

    @Test
    fun invoke_validDraft_persistsEventInRepository() = runTest {
        val draft = QuickAddDraft(
            rawInput = "Team standup tomorrow 10am !high #Work",
            title = "Team standup",
            date = LocalDate.of(2026, 10, 22),
            time = LocalTime.of(10, 0),
            isAllDay = false,
            priority = Priority.HIGH,
            categoryName = "Work",
            reminderMinutesBefore = 15
        )

        val result = createEventFromQuickAddUseCase(draft)
        assertTrue(result is Result.Success)

        val savedEvent = (result as Result.Success).data
        assertEquals("Team standup", savedEvent.title)
        assertEquals(Priority.HIGH, savedEvent.priority)
        assertEquals("cat_work", savedEvent.categoryId)
        assertEquals(1, savedEvent.notificationSchedules.size)

        // Verify event in repository
        val inRepo = eventRepository.getEvent(savedEvent.id)
        assertNotNull(inRepo)
        assertEquals("Team standup", inRepo?.title)
    }

    @Test
    fun invoke_createsNewCategory_whenCategoryDoesNotExist() = runTest {
        val draft = QuickAddDraft(
            rawInput = "Dentist appointment #Health",
            title = "Dentist appointment",
            date = LocalDate.of(2026, 10, 23),
            categoryName = "Health"
        )

        val result = createEventFromQuickAddUseCase(draft)
        assertTrue(result is Result.Success)

        val categories = categoryRepository.getAllCategories().first()
        val healthCat = categories.find { it.name == "Health" }
        assertNotNull("Health category should have been created", healthCat)
        assertEquals(healthCat?.id, (result as Result.Success).data.categoryId)
    }

    @Test
    fun invoke_createsGroupsAndTags_whenTheyDoNotExist() = runTest {
        val draft = QuickAddDraft(
            rawInput = "Deliver package @Errands +courier",
            title = "Deliver package",
            date = LocalDate.of(2026, 10, 24),
            groupNames = listOf("Errands"),
            tagNames = listOf("courier")
        )

        val result = createEventFromQuickAddUseCase(draft)
        assertTrue(result is Result.Success)

        val event = (result as Result.Success).data
        assertEquals(1, event.groupIds.size)
        assertEquals(1, event.tagIds.size)

        val groups = eventGroupRepository.getAllGroups().first()
        assertTrue(groups.any { it.name == "Errands" })

        val tags = tagRepository.getAllTags().first()
        assertTrue(tags.any { it.name == "courier" })
    }

    @Test
    fun invoke_recurringDraft_persistsRecurrenceRule() = runTest {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1)
        val draft = QuickAddDraft(
            rawInput = "Daily vitamins daily",
            title = "Daily vitamins",
            date = LocalDate.of(2026, 10, 21),
            recurrenceRule = rule
        )

        val result = createEventFromQuickAddUseCase(draft)
        assertTrue(result is Result.Success)

        val event = (result as Result.Success).data
        assertTrue(event.isRecurring)
        assertEquals(RecurrenceFrequency.DAILY, event.recurrenceRule?.frequency)
    }

    @Test
    fun invoke_invalidDraft_returnsFailure() = runTest {
        val draft = QuickAddDraft(
            rawInput = "",
            title = "",
            validationErrors = listOf("Event title is required")
        )

        val result = createEventFromQuickAddUseCase(draft)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun invoke_reusesExistingCategoryGroupAndTag_withoutCreatingDuplicates() = runTest {
        // Pre-populate category "Work", group "TeamAlpha", tag "Urgent"
        val existingGroup = EventGroup(id = "grp_team", name = "TeamAlpha", createdAt = timeProvider.nowInstant(), updatedAt = timeProvider.nowInstant())
        eventGroupRepository.saveGroup(existingGroup)

        val existingTag = Tag(id = "tag_urg", name = "Urgent", normalizedName = Tag.normalize("Urgent"))
        tagRepository.saveTag(existingTag)

        val draft = QuickAddDraft(
            rawInput = "Sync meeting #work @teamalpha +urgent",
            title = "Sync meeting",
            date = LocalDate.of(2026, 10, 22),
            categoryName = "work", // case-insensitive match to cat_work
            groupNames = listOf("TeamAlpha"),
            tagNames = listOf("Urgent")
        )

        val result = createEventFromQuickAddUseCase(draft)
        assertTrue(result is Result.Success)

        val event = (result as Result.Success).data
        assertEquals("cat_work", event.categoryId)
        assertEquals(setOf("grp_team"), event.groupIds)
        assertEquals(setOf("tag_urg"), event.tagIds)

        // Verify no duplicate entities were saved
        assertEquals(1, categoryRepository.getAllCategories().first().size)
        assertEquals(1, eventGroupRepository.getAllGroups().first().size)
        assertEquals(1, tagRepository.getAllTags().first().size)
    }

    @Test
    fun invoke_reminderTimingAndDefaults_createsNotificationSchedulesCorrectly() = runTest {
        // rem:15m
        val draft15 = QuickAddDraft(
            rawInput = "Test rem:15m",
            title = "Test 15",
            date = LocalDate.of(2026, 10, 22),
            reminderMinutesBefore = 15
        )
        val res15 = createEventFromQuickAddUseCase(draft15)
        assertTrue(res15 is Result.Success)
        val sched15 = (res15 as Result.Success).data.notificationSchedules.first()
        assertEquals(NotificationTriggerType.BEFORE_START, sched15.triggerType)
        assertEquals(Duration.ofMinutes(15), sched15.offsetDuration)
        assertEquals(ReminderDeliveryType.NOTIFICATION, sched15.deliveryType)
        assertEquals(SoundSourceType.DEFAULT, sched15.sound.type)

        // rem:0m / rem:at-time
        val draftAtStart = QuickAddDraft(
            rawInput = "Test rem:at-time",
            title = "Test at-time",
            date = LocalDate.of(2026, 10, 22),
            reminderMinutesBefore = 0
        )
        val resAtStart = createEventFromQuickAddUseCase(draftAtStart)
        assertTrue(resAtStart is Result.Success)
        val schedAtStart = (resAtStart as Result.Success).data.notificationSchedules.first()
        assertEquals(NotificationTriggerType.AT_START, schedAtStart.triggerType)
        assertEquals(Duration.ZERO, schedAtStart.offsetDuration)
        assertEquals(ReminderDeliveryType.NOTIFICATION, schedAtStart.deliveryType)

        // No reminder
        val draftNoRem = QuickAddDraft(
            rawInput = "Test without reminder",
            title = "Test no rem",
            date = LocalDate.of(2026, 10, 22),
            reminderMinutesBefore = null
        )
        val resNoRem = createEventFromQuickAddUseCase(draftNoRem)
        assertTrue(resNoRem is Result.Success)
        assertTrue((resNoRem as Result.Success).data.notificationSchedules.isEmpty())
    }
}
