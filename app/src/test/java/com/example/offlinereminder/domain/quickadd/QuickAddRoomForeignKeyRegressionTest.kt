package com.example.offlinereminder.domain.quickadd

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.repository.CategoryRepositoryImpl
import com.example.offlinereminder.data.repository.EventGroupRepositoryImpl
import com.example.offlinereminder.data.repository.EventTypeRepositoryImpl
import com.example.offlinereminder.data.repository.OfflineEventRepository
import com.example.offlinereminder.data.repository.SavedLocationRepositoryImpl
import com.example.offlinereminder.data.repository.TagRepositoryImpl
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class QuickAddRoomForeignKeyRegressionTest {

    private lateinit var db: AppDatabase
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var eventTypeRepository: EventTypeRepositoryImpl
    private lateinit var eventGroupRepository: EventGroupRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var savedLocationRepository: SavedLocationRepositoryImpl
    private lateinit var eventRepository: OfflineEventRepository
    private lateinit var createEventUseCase: CreateEventUseCase
    private lateinit var createEventFromQuickAddUseCase: CreateEventFromQuickAddUseCase

    private val fixedInstant = Instant.parse("2026-10-21T10:00:00Z")
    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = fixedInstant,
        currentZoneId = utcZone
    )

    private val parser = QuickAddParser(timeProvider = timeProvider)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Create clean in-memory Room database with SQLite foreign keys strictly enforced
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        categoryRepository = CategoryRepositoryImpl(db.categoryDao())
        eventTypeRepository = EventTypeRepositoryImpl(db.eventTypeDao())
        eventGroupRepository = EventGroupRepositoryImpl(db.eventGroupDao())
        tagRepository = TagRepositoryImpl(db.tagDao())
        savedLocationRepository = SavedLocationRepositoryImpl(db.savedLocationDao())
        eventRepository = OfflineEventRepository(db, timeProvider)

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
            timeProvider = timeProvider,
            savedLocationRepository = savedLocationRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cleanDatabase_doctorTomorrow3pm_succeedsWithoutForeignKeyFailure() = runTest {
        // 1. Verify completely clean database (0 categories, 0 event types)
        val initialCategories = categoryRepository.getAllCategories().first()
        val initialTypes = eventTypeRepository.getAllEventTypes().first()
        assertEquals(0, initialCategories.size)
        assertEquals(0, initialTypes.size)

        // 2. Parse exactly "doctor Tomorrow 3 pm"
        val input = "doctor Tomorrow 3 pm"
        val draft = parser.parse(input)
        assertTrue(draft.isValid)
        assertEquals("doctor", draft.title)
        assertEquals(LocalDate.of(2026, 10, 22), draft.date)
        assertEquals(LocalTime.of(15, 0), draft.time)

        // 3. Execute persistence through CreateEventFromQuickAddUseCase
        val saveResult = createEventFromQuickAddUseCase(draft)
        assertTrue("Expected Success but got: $saveResult", saveResult is Result.Success)
        val createdEvent = (saveResult as Result.Success).data

        // 4. Verify the event exists in Room SQLite and referential integrity is preserved
        val retrieved = eventRepository.getEvent(createdEvent.id)
        assertNotNull(retrieved)
        assertEquals("doctor", retrieved?.title)

        // 5. Verify the category was provisioned and persisted in SQLite
        val category = categoryRepository.getCategory(createdEvent.categoryId)
        assertNotNull(category)

        // 6. Verify the event type was provisioned and persisted in SQLite
        val eventType = eventTypeRepository.getEventType(createdEvent.eventTypeId)
        assertNotNull(eventType)
        assertEquals(createdEvent.categoryId, eventType?.categoryId)
    }

    @Test
    fun cleanDatabase_quickAddWithAllFields_priorityTagCategoryGroupLocationReminderRecurrence() = runTest {
        // Comprehensive Quick Add draft with all metadata dimensions
        val input = "Cardiologist Consultation tomorrow 2pm !urgent #Health @Clinic rem:30m every month"
        val draft = parser.parse(input)
        assertTrue(draft.isValid)
        assertEquals(Priority.CRITICAL, draft.priority)
        assertEquals("Health", draft.categoryName)
        assertEquals(30, draft.reminderMinutesBefore)
        assertEquals(RecurrenceFrequency.MONTHLY, draft.recurrenceRule?.frequency)

        val saveResult = createEventFromQuickAddUseCase(draft)
        assertTrue("Expected Success but got: $saveResult", saveResult is Result.Success)
        val event = (saveResult as Result.Success).data

        val retrieved = eventRepository.getEvent(event.id)
        assertNotNull(retrieved)
        assertEquals(Priority.CRITICAL, retrieved?.priority)
        assertEquals(1, retrieved?.notificationSchedules?.size)
        assertEquals(RecurrenceFrequency.MONTHLY, retrieved?.recurrenceRule?.frequency)
    }

    @Test
    fun populatedDatabase_doctorTomorrow3pm_matchesDoctorEventType() = runTest {
        // Seed default categories and event types (similar to PrepopulateCallback)
        categoryRepository.saveCategory(com.example.offlinereminder.domain.model.Category("cat_health", "Health", 0xFF9C27B0.toInt()))
        eventTypeRepository.saveEventType(
            com.example.offlinereminder.domain.model.EventType(
                id = "type_doctor",
                name = "Doctor",
                key = "doctor_appointment",
                categoryId = "cat_health",
                createdAt = fixedInstant
            )
        )

        val draft = parser.parse("doctor Tomorrow 3 pm")
        val result = createEventFromQuickAddUseCase(draft)
        assertTrue(result is Result.Success)
        val event = (result as Result.Success).data

        assertEquals("type_doctor", event.eventTypeId)
    }
}
