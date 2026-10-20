package com.example.offlinereminder.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Tag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MetadataRepositoriesTest {

    private lateinit var database: AppDatabase
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var eventTypeRepository: EventTypeRepositoryImpl
    private lateinit var eventGroupRepository: EventGroupRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var eventRepository: OfflineEventRepository

    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        categoryRepository = CategoryRepositoryImpl(database.categoryDao())
        eventTypeRepository = EventTypeRepositoryImpl(database.eventTypeDao())
        eventGroupRepository = EventGroupRepositoryImpl(database.eventGroupDao())
        tagRepository = TagRepositoryImpl(database.tagDao())
        eventRepository = OfflineEventRepository(database = database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun categoryRepository_crudAndFlow() = runTest {
        val category = Category(
            id = "cat-health",
            name = "Health & Wellness",
            color = 0xFF00FF00.toInt(),
            icon = "health_icon",
            isCustom = true,
            sortOrder = 2
        )

        val saveResult = categoryRepository.saveCategory(category)
        assertTrue(saveResult is Result.Success)

        val retrieved = categoryRepository.getCategory("cat-health")
        assertNotNull(retrieved)
        assertEquals("Health & Wellness", retrieved?.name)
        assertTrue(retrieved?.isCustom == true)

        val categories = categoryRepository.getAllCategories().first()
        assertEquals(1, categories.size)
        assertEquals("Health & Wellness", categories[0].name)

        val deleteResult = categoryRepository.deleteCategory("cat-health")
        assertTrue(deleteResult is Result.Success)
        assertNull(categoryRepository.getCategory("cat-health"))
    }

    @Test
    fun eventTypeRepository_crudSearchAndCategoryFilter() = runTest {
        categoryRepository.saveCategory(
            Category(id = "cat-education", name = "Education", sortOrder = 1)
        )

        val examType = EventType(
            id = "type-exam",
            name = "Final Exam",
            key = "edu_final_exam",
            categoryId = "cat-education",
            aliases = listOf("finals", "test", "quiz"),
            createdAt = testInstant
        )
        val assignmentType = EventType(
            id = "type-assignment",
            name = "Homework Assignment",
            key = "edu_assignment",
            categoryId = "cat-education",
            aliases = listOf("homework", "task"),
            createdAt = testInstant
        )

        eventTypeRepository.saveEventType(examType)
        eventTypeRepository.saveEventType(assignmentType)

        // Key lookup
        val byKey = eventTypeRepository.getEventTypeByKey("edu_final_exam")
        assertNotNull(byKey)
        assertEquals("Final Exam", byKey?.name)

        // Category filter
        val byCat = eventTypeRepository.getEventTypesByCategory("cat-education").first()
        assertEquals(2, byCat.size)

        // Alias search
        val searched = eventTypeRepository.searchEventTypes("quiz").first()
        assertEquals(1, searched.size)
        assertEquals("Final Exam", searched[0].name)

        eventTypeRepository.deleteEventType("type-exam")
        assertNull(eventTypeRepository.getEventType("type-exam"))
    }

    @Test
    fun eventGroupRepository_crudAndMembership() = runTest {
        categoryRepository.saveCategory(Category(id = "cat-work", name = "Work"))
        eventTypeRepository.saveEventType(
            EventType(id = "type-work", name = "Work Task", key = "work_task", categoryId = "cat-work", createdAt = testInstant)
        )

        val group = EventGroup(
            id = "grp-launch",
            name = "Product Launch",
            description = "Q4 Product Launch tasks",
            createdAt = testInstant,
            updatedAt = testInstant
        )
        val saveGroupResult = eventGroupRepository.saveGroup(group)
        assertTrue(saveGroupResult is Result.Success)

        val retrievedGroup = eventGroupRepository.getGroup("grp-launch")
        assertNotNull(retrievedGroup)
        assertEquals("Product Launch", retrievedGroup?.name)

        // Create an event and associate with group
        val event = Event(
            id = "evt-launch-prep",
            title = "Finalize Press Release",
            eventTypeId = "type-work",
            categoryId = "cat-work",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 11, 10)),
            createdAt = testInstant,
            updatedAt = testInstant
        )
        eventRepository.saveEvent(event)

        // Add event to group
        val addResult = eventGroupRepository.addEventToGroup("evt-launch-prep", "grp-launch")
        assertTrue(addResult is Result.Success)

        val groupEvents = eventGroupRepository.getActiveEventsForGroup("grp-launch").first()
        assertEquals(1, groupEvents.size)
        assertEquals("Finalize Press Release", groupEvents[0].title)

        val groupsForEvent = eventGroupRepository.getGroupsForEvent("evt-launch-prep").first()
        assertEquals(1, groupsForEvent.size)
        assertEquals("Product Launch", groupsForEvent[0].name)

        // Remove event from group
        val removeResult = eventGroupRepository.removeEventFromGroup("evt-launch-prep", "grp-launch")
        assertTrue(removeResult is Result.Success)
        assertTrue(eventGroupRepository.getActiveEventsForGroup("grp-launch").first().isEmpty())

        // Event itself remains untouched
        assertNotNull(eventRepository.getEvent("evt-launch-prep"))
    }

    @Test
    fun tagRepository_crudSearchAndMembership() = runTest {
        categoryRepository.saveCategory(Category(id = "cat-work", name = "Work"))
        eventTypeRepository.saveEventType(
            EventType(id = "type-work", name = "Work Task", key = "work_task", categoryId = "cat-work", createdAt = testInstant)
        )

        val tag = Tag(id = "tag-urgent", name = "#Urgent", color = 0xFFFF0000.toInt())
        val saveTagResult = tagRepository.saveTag(tag)
        assertTrue(saveTagResult is Result.Success)

        // Normalized name lookup
        val retrieved = tagRepository.getTagByNormalizedName("urgent")
        assertNotNull(retrieved)
        assertEquals("#Urgent", retrieved?.name)
        assertEquals("urgent", retrieved?.normalizedName)

        val event = Event(
            id = "evt-tag-target",
            title = "Server Upgrade",
            eventTypeId = "type-work",
            categoryId = "cat-work",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 11, 12)),
            createdAt = testInstant,
            updatedAt = testInstant
        )
        eventRepository.saveEvent(event)

        tagRepository.addTagToEvent("evt-tag-target", "tag-urgent")
        val tagsForEvent = tagRepository.getTagsForEvent("evt-tag-target").first()
        assertEquals(1, tagsForEvent.size)
        assertEquals("#Urgent", tagsForEvent[0].name)

        val activeEventsForTag = tagRepository.getActiveEventsForTag("tag-urgent").first()
        assertEquals(1, activeEventsForTag.size)
        assertEquals("Server Upgrade", activeEventsForTag[0].title)

        tagRepository.removeTagFromEvent("evt-tag-target", "tag-urgent")
        assertTrue(tagRepository.getTagsForEvent("evt-tag-target").first().isEmpty())
    }
}
