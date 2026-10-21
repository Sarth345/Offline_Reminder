package com.example.offlinereminder.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.presentation.dashboard.DashboardViewModel
import com.example.offlinereminder.presentation.eventdetail.EventDetailViewModel
import com.example.offlinereminder.presentation.eventeditor.EventEditorViewModel
import com.example.offlinereminder.presentation.eventlist.EventListViewModel
import com.example.offlinereminder.presentation.upcoming.UpcomingViewModel
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {

    private lateinit var container: AppContainer
    private lateinit var factory: ViewModelFactory

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        container = DefaultAppContainer(context)
        factory = ViewModelFactory(container)
    }

    @Test
    fun appContainer_providesAllRepositoriesAndUseCases() {
        assertNotNull(container.database)
        assertNotNull(container.timeProvider)
        assertNotNull(container.dispatcherProvider)
        assertNotNull(container.alarmScheduler)
        assertNotNull(container.recurrenceEngine)

        assertNotNull(container.eventRepository)
        assertNotNull(container.categoryRepository)
        assertNotNull(container.eventTypeRepository)
        assertNotNull(container.eventGroupRepository)
        assertNotNull(container.tagRepository)
        assertNotNull(container.occurrenceExceptionRepository)
        assertNotNull(container.notificationRepository)
        assertNotNull(container.customSoundRepository)
        assertNotNull(container.notificationProfileRepository)
        assertNotNull(container.eventTemplateRepository)

        assertNotNull(container.createEventUseCase)
        assertNotNull(container.updateEventUseCase)
        assertNotNull(container.deleteEventUseCase)
        assertNotNull(container.getEventByIdUseCase)
        assertNotNull(container.getEventsUseCase)
        assertNotNull(container.getCategoriesUseCase)
        assertNotNull(container.getEventTypesUseCase)
        assertNotNull(container.getEventGroupsUseCase)
        assertNotNull(container.getTagsUseCase)
        assertNotNull(container.getDashboardOccurrencesUseCase)
        assertNotNull(container.getUpcomingOccurrencesUseCase)
        assertNotNull(container.completeOccurrenceActionUseCase)
        assertNotNull(container.skipOccurrenceActionUseCase)
        assertNotNull(container.rescheduleOccurrenceActionUseCase)
    }

    @Test
    fun viewModelFactory_createsAllViewModels() {
        val dashboardVm = factory.create(DashboardViewModel::class.java)
        assertNotNull(dashboardVm)

        val eventListVm = factory.create(EventListViewModel::class.java)
        assertNotNull(eventListVm)

        val eventDetailVm = factory.create(EventDetailViewModel::class.java)
        assertNotNull(eventDetailVm)

        val eventEditorVm = factory.create(EventEditorViewModel::class.java)
        assertNotNull(eventEditorVm)

        val upcomingVm = factory.create(UpcomingViewModel::class.java)
        assertNotNull(upcomingVm)
    }
}
