package com.example.offlinereminder.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.result.getOrNull
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.repository.CategoryRepositoryImpl
import com.example.offlinereminder.data.repository.EventTypeRepositoryImpl
import com.example.offlinereminder.data.repository.OfflineEventRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.Priority
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
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class CategoryAndEventTypeManagementUseCasesTest {

    private lateinit var db: AppDatabase
    private lateinit var categoryRepo: CategoryRepositoryImpl
    private lateinit var eventTypeRepo: EventTypeRepositoryImpl
    private lateinit var eventRepo: OfflineEventRepository

    private lateinit var createCategoryUseCase: CreateCategoryUseCase
    private lateinit var updateCategoryUseCase: UpdateCategoryUseCase
    private lateinit var deleteCategoryUseCase: DeleteCategoryUseCase

    private lateinit var createEventTypeUseCase: CreateEventTypeUseCase
    private lateinit var updateEventTypeUseCase: UpdateEventTypeUseCase
    private lateinit var deleteEventTypeUseCase: DeleteEventTypeUseCase

    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")
    private val timeProvider = TestTimeProvider(
        currentInstant = testInstant,
        currentZoneId = ZoneId.of("UTC")
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        categoryRepo = CategoryRepositoryImpl(db.categoryDao())
        eventTypeRepo = EventTypeRepositoryImpl(db.eventTypeDao())
        eventRepo = OfflineEventRepository(db, timeProvider)

        createCategoryUseCase = CreateCategoryUseCase(categoryRepo)
        updateCategoryUseCase = UpdateCategoryUseCase(categoryRepo)
        deleteCategoryUseCase = DeleteCategoryUseCase(categoryRepo, eventRepo, eventTypeRepo)

        createEventTypeUseCase = CreateEventTypeUseCase(eventTypeRepo, categoryRepo, timeProvider)
        updateEventTypeUseCase = UpdateEventTypeUseCase(eventTypeRepo, categoryRepo)
        deleteEventTypeUseCase = DeleteEventTypeUseCase(eventTypeRepo, eventRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createCategory_success_and_duplicateRejection() = runTest {
        val result = createCategoryUseCase(name = "Personal Finance")
        assertTrue(result.isSuccess)
        val cat = result.getOrNull()
        assertNotNull(cat)
        assertEquals("Personal Finance", cat?.name)

        // Duplicate name rejected
        val dupResult = createCategoryUseCase(name = "personal finance")
        assertTrue(dupResult.isFailure)
        assertTrue((dupResult as Result.Failure).message!!.contains("already exists"))

        // Blank name rejected
        val blankResult = createCategoryUseCase(name = "   ")
        assertTrue(blankResult.isFailure)
        assertTrue((blankResult as Result.Failure).message!!.contains("cannot be blank"))
    }

    @Test
    fun updateCategory_success_and_duplicateRejection() = runTest {
        val cat1 = createCategoryUseCase(name = "Cat One").getOrNull()!!
        val cat2 = createCategoryUseCase(name = "Cat Two").getOrNull()!!

        // Valid update
        val updateRes = updateCategoryUseCase(cat1.copy(name = "Renamed Cat One"))
        assertTrue(updateRes.isSuccess)
        assertEquals("Renamed Cat One", categoryRepo.getCategory(cat1.id)?.name)

        // Updating to clash with Cat Two should fail
        val clashRes = updateCategoryUseCase(cat1.copy(name = "Cat Two"))
        assertTrue(clashRes.isFailure)
        assertTrue((clashRes as Result.Failure).message!!.contains("already exists"))
    }

    @Test
    fun deleteCategory_unreferenced_deletesSuccessfully() = runTest {
        val cat = createCategoryUseCase(name = "Temporary").getOrNull()!!
        val delRes = deleteCategoryUseCase(cat.id)
        assertTrue(delRes.isSuccess)
        assertEquals(null, categoryRepo.getCategory(cat.id))
    }

    @Test
    fun deleteCategory_referencedByEvent_failsWithReferentialSafety() = runTest {
        val cat = createCategoryUseCase(name = "Health").getOrNull()!!
        val type = createEventTypeUseCase(name = "Dentist", categoryId = cat.id).getOrNull()!!

        // Create an event referencing this category
        val event = Event(
            id = "evt-health",
            title = "Dentist Visit",
            eventTypeId = type.id,
            categoryId = cat.id,
            priority = Priority.NORMAL,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = testInstant,
            updatedAt = testInstant
        )
        eventRepo.saveEvent(event)

        val delRes = deleteCategoryUseCase(cat.id)
        assertTrue(delRes.isFailure)
        assertTrue((delRes as Result.Failure).message!!.contains("in use by 1 reminder(s)"))

        // Category still exists
        assertNotNull(categoryRepo.getCategory(cat.id))
    }

    @Test
    fun deleteCategory_referencedByEventType_failsWithReferentialSafety() = runTest {
        val cat = createCategoryUseCase(name = "Education").getOrNull()!!
        createEventTypeUseCase(name = "Exam", categoryId = cat.id).getOrNull()!!

        val delRes = deleteCategoryUseCase(cat.id)
        assertTrue(delRes.isFailure)
        assertTrue((delRes as Result.Failure).message!!.contains("in use by 1 event type(s)"))
    }

    @Test
    fun createEventType_success_and_validations() = runTest {
        val cat = createCategoryUseCase(name = "Chores").getOrNull()!!

        val typeRes = createEventTypeUseCase(name = "Cleaning", categoryId = cat.id)
        assertTrue(typeRes.isSuccess)
        assertEquals("Cleaning", typeRes.getOrNull()?.name)

        // Non-existent category fails
        val badCatRes = createEventTypeUseCase(name = "Cooking", categoryId = "non-existent")
        assertTrue(badCatRes.isFailure)
        assertTrue((badCatRes as Result.Failure).message!!.contains("Category not found"))

        // Duplicate name fails
        val dupRes = createEventTypeUseCase(name = "cleaning", categoryId = cat.id)
        assertTrue(dupRes.isFailure)
        assertTrue((dupRes as Result.Failure).message!!.contains("already exists"))
    }

    @Test
    fun deleteEventType_referencedByEvent_failsWithReferentialSafety() = runTest {
        val cat = createCategoryUseCase(name = "Work").getOrNull()!!
        val type = createEventTypeUseCase(name = "Sprint", categoryId = cat.id).getOrNull()!!

        val event = Event(
            id = "evt-work",
            title = "Sprint Planning",
            eventTypeId = type.id,
            categoryId = cat.id,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = testInstant,
            updatedAt = testInstant
        )
        eventRepo.saveEvent(event)

        val delRes = deleteEventTypeUseCase(type.id)
        assertTrue(delRes.isFailure)
        assertTrue((delRes as Result.Failure).message!!.contains("in use by 1 reminder(s)"))

        // Delete event, then deletion should succeed
        eventRepo.deleteEvent(event.id)
        val delResAfter = deleteEventTypeUseCase(type.id)
        assertTrue(delResAfter.isSuccess)
    }

    @Test
    fun deleteCategory_referencedByArchivedOrTrashedEvent_failsWithReferentialSafety() = runTest {
        val cat = createCategoryUseCase(name = "Health").getOrNull()!!
        val type = createEventTypeUseCase(name = "Dentist", categoryId = cat.id).getOrNull()!!

        val archivedEvent = Event(
            id = "evt-archived",
            title = "Annual Checkup",
            eventTypeId = type.id,
            categoryId = cat.id,
            lifecycleStatus = com.example.offlinereminder.domain.model.EventLifecycleStatus.ARCHIVED,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = testInstant,
            updatedAt = testInstant
        )
        eventRepo.saveEvent(archivedEvent)

        // Deleting category must fail because archived event still references it
        val delCatRes = deleteCategoryUseCase(cat.id)
        assertTrue(delCatRes.isFailure)
        assertTrue((delCatRes as Result.Failure).message!!.contains("in use by 1 reminder(s)"))

        // Deleting event type must also fail because archived event still references it
        val delTypeRes = deleteEventTypeUseCase(type.id)
        assertTrue(delTypeRes.isFailure)
        assertTrue((delTypeRes as Result.Failure).message!!.contains("in use by 1 reminder(s)"))

        // Move to trash
        eventRepo.moveToTrash(archivedEvent.id)

        // Trashed event still references both, so deletions must still fail
        val delCatResTrashed = deleteCategoryUseCase(cat.id)
        assertTrue(delCatResTrashed.isFailure)
        assertTrue((delCatResTrashed as Result.Failure).message!!.contains("in use by 1 reminder(s)"))

        val delTypeResTrashed = deleteEventTypeUseCase(type.id)
        assertTrue(delTypeResTrashed.isFailure)
        assertTrue((delTypeResTrashed as Result.Failure).message!!.contains("in use by 1 reminder(s)"))
    }
}
