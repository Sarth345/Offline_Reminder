package com.example.offlinereminder.domain.location

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemorySavedLocationRepository
import com.example.offlinereminder.domain.model.SavedLocation
import com.example.offlinereminder.domain.usecase.DeleteSavedLocationUseCase
import com.example.offlinereminder.domain.usecase.GetSavedLocationsUseCase
import com.example.offlinereminder.domain.usecase.SaveSavedLocationUseCase
import com.example.offlinereminder.domain.usecase.ToggleSavedLocationFavoriteUseCase
import kotlinx.coroutines.flow.first
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

class SavedLocationTest {

    private lateinit var repository: InMemorySavedLocationRepository
    private lateinit var getLocationsUseCase: GetSavedLocationsUseCase
    private lateinit var saveLocationUseCase: SaveSavedLocationUseCase
    private lateinit var deleteLocationUseCase: DeleteSavedLocationUseCase
    private lateinit var toggleFavoriteUseCase: ToggleSavedLocationFavoriteUseCase

    @Before
    fun setUp() {
        repository = InMemorySavedLocationRepository()
        val timeProvider = TestTimeProvider(
            currentInstant = Instant.parse("2026-10-21T10:00:00Z"),
            currentZoneId = ZoneId.of("UTC")
        )
        getLocationsUseCase = GetSavedLocationsUseCase(repository)
        saveLocationUseCase = SaveSavedLocationUseCase(repository, timeProvider)
        deleteLocationUseCase = DeleteSavedLocationUseCase(repository)
        toggleFavoriteUseCase = ToggleSavedLocationFavoriteUseCase(repository)
    }

    @Test
    fun saveAndRetrieveLocation() = runTest {
        val saveResult = saveLocationUseCase(
            name = "Office HQ",
            address = "500 Howard Street, San Francisco",
            building = "Building B",
            room = "Suite 300",
            latitude = 37.789,
            longitude = -122.396,
            notes = "Badge required at front desk",
            isFavorite = true
        )

        assertTrue(saveResult is Result.Success)
        val saved = (saveResult as Result.Success).data
        assertEquals("Office HQ", saved.name)
        assertTrue(saved.isFavorite)

        val all = getLocationsUseCase().first()
        assertEquals(1, all.size)
        assertEquals("Office HQ", all.first().name)

        val favorites = getLocationsUseCase.getFavorites().first()
        assertEquals(1, favorites.size)
    }

    @Test
    fun blankNameValidation_fails() = runTest {
        val saveResult = saveLocationUseCase(name = "   ")
        assertTrue(saveResult is Result.Failure)
    }

    @Test
    fun toggleFavoriteAndDeletion() = runTest {
        val saveResult = saveLocationUseCase(name = "Gym", isFavorite = false)
        val id = (saveResult as Result.Success).data.id

        toggleFavoriteUseCase(id)
        val toggled = repository.getLocationById(id)
        assertTrue(toggled?.isFavorite == true)

        deleteLocationUseCase(id)
        assertNull(repository.getLocationById(id))
    }

    @Test
    fun editSavedLocation_updatesFieldsPreservingId() = runTest {
        val saveResult = saveLocationUseCase(
            name = "Office HQ",
            address = "500 Howard Street",
            building = "Bld A"
        )
        val id = (saveResult as Result.Success).data.id

        // Edit location with updated building & room
        val editResult = saveLocationUseCase(
            id = id,
            name = "Office HQ",
            address = "500 Howard Street",
            building = "Bld B",
            room = "Floor 4"
        )
        assertTrue(editResult is Result.Success)
        val updated = repository.getLocationById(id)
        assertNotNull(updated)
        assertEquals("Bld B", updated?.building)
        assertEquals("Floor 4", updated?.room)
    }

    @Test
    fun searchSavedLocations_matchesNameOrAddress() = runTest {
        saveLocationUseCase(name = "Downtown Library", address = "100 4th St")
        saveLocationUseCase(name = "Central Park", address = "79th St Transverse")

        val searchResult = repository.searchLocationsFlow("library").first()
        assertEquals(1, searchResult.size)
        assertEquals("Downtown Library", searchResult.first().name)

        val addressSearchResult = repository.searchLocationsFlow("transverse").first()
        assertEquals(1, addressSearchResult.size)
        assertEquals("Central Park", addressSearchResult.first().name)
    }

    @Test
    fun deletingSavedLocation_doesNotCorruptEventLocation() = runTest {
        val saveResult = saveLocationUseCase(name = "Coffee Shop", address = "Main St")
        val saved = (saveResult as Result.Success).data

        // Embedded event location keeps its values independently
        val eventLocation = saved.toLocationData()
        assertEquals("Coffee Shop", eventLocation.placeName)
        assertEquals("Main St", eventLocation.addressText)

        // Delete from saved locations
        deleteLocationUseCase(saved.id)
        assertNull(repository.getLocationById(saved.id))

        // Event's embedded copy remains valid and intact
        assertEquals("Coffee Shop", eventLocation.placeName)
        assertEquals("Main St", eventLocation.addressText)
        assertTrue(eventLocation.isNotEmpty)
    }
}
