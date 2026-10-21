package com.example.offlinereminder.presentation.quickadd

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.quickadd.CreateEventFromQuickAddUseCase
import com.example.offlinereminder.domain.quickadd.QuickAddParser
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

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

    private lateinit var parser: QuickAddParser
    private lateinit var createEventUseCase: CreateEventUseCase
    private lateinit var createEventFromQuickAddUseCase: CreateEventFromQuickAddUseCase
    private lateinit var viewModel: QuickAddViewModel

    @Before
    fun setUp() = runTest {
        eventRepository = InMemoryEventRepository()
        categoryRepository = InMemoryCategoryRepository()
        eventTypeRepository = InMemoryEventTypeRepository()
        eventGroupRepository = InMemoryEventGroupRepository()
        tagRepository = InMemoryTagRepository()

        categoryRepository.saveCategory(Category(id = "cat_1", name = "Work", color = 0xFF4285F4.toInt()))
        eventTypeRepository.saveEventType(
            EventType(
                id = "type_1",
                name = "Task",
                key = "task",
                categoryId = "cat_1",
                createdAt = timeProvider.nowInstant()
            )
        )

        parser = QuickAddParser(timeProvider)
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

        viewModel = QuickAddViewModel(
            parser = parser,
            createEventFromQuickAddUseCase = createEventFromQuickAddUseCase,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun initial_stateIsEmpty() {
        val state = viewModel.uiState.value
        assertEquals("", state.input)
        assertNull(state.draft)
        assertFalse(state.isSaving)
        assertFalse(state.saveSuccess)
        assertFalse(state.canSave)
    }

    @Test
    fun onAction_updateInput_parsesLive() {
        viewModel.onAction(QuickAddAction.UpdateInput("Meeting tomorrow at 3pm !high #Work"))

        val state = viewModel.uiState.value
        assertNotNull(state.draft)
        assertEquals("Meeting", state.draft?.title)
        assertTrue(state.canSave)
    }

    @Test
    fun onAction_save_persistsAndSetsSuccess() = runTest(testDispatcher) {
        viewModel.onAction(QuickAddAction.UpdateInput("Submit report tomorrow 11am !high #Work"))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(QuickAddAction.Save)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.saveSuccess)
        assertNotNull(state.savedEventId)

        val inRepo = eventRepository.getEvent(state.savedEventId!!)
        assertNotNull(inRepo)
        assertEquals("Submit report", inRepo?.title)
    }

    @Test
    fun onAction_reset_clearsState() = runTest(testDispatcher) {
        viewModel.onAction(QuickAddAction.UpdateInput("Test task"))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(QuickAddAction.Reset)
        val state = viewModel.uiState.value
        assertEquals("", state.input)
        assertNull(state.draft)
        assertFalse(state.saveSuccess)
    }
}
