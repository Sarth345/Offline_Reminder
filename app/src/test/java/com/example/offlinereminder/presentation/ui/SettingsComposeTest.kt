package com.example.offlinereminder.presentation.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.backup.RestoreSummary
import com.example.offlinereminder.presentation.settings.SettingsAction
import com.example.offlinereminder.presentation.settings.SettingsContent
import com.example.offlinereminder.presentation.settings.SettingsUiState
import com.example.offlinereminder.ui.theme.OfflineReminderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(packageName = "com.example.offlinereminder")
class SettingsComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsContent_displaysHeaderAndBackupOptions() {
        var actionReceived: SettingsAction? = null
        var exportClicked = false
        var importClicked = false

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = SettingsUiState(),
                    onAction = { actionReceived = it },
                    onNavigateBack = {},
                    onExportClick = { exportClicked = true },
                    onImportClick = { importClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Settings & Data Management").assertIsDisplayed()
        composeTestRule.onNodeWithText("Local Backup & Restore").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Export Backup").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Import Backup File").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("Export Backup").performScrollTo().performClick()
        assertTrue(exportClicked)

        composeTestRule.onNodeWithText("Import Backup File").performScrollTo().performClick()
        assertTrue(importClicked)
    }

    @Test
    fun settingsContent_selectsRestoreStrategy() {
        var actionReceived: SettingsAction? = null

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = SettingsUiState(),
                    onAction = { actionReceived = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Merge & Overwrite").performScrollTo().performClick()
        assertEquals(SettingsAction.SelectRestoreStrategy(RestoreStrategy.MERGE_OVERWRITE), actionReceived)
    }

    @Test
    fun settingsContent_displaysErrorAndSuccessMessages() {
        val state = SettingsUiState(
            errorMessage = "Restore failed: Invalid schema",
            successMessage = "Backup exported successfully",
            lastRestoreSummary = RestoreSummary(eventsRestored = 10, categoriesRestored = 3)
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = state,
                    onAction = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Restore failed: Invalid schema").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Backup exported successfully").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Last Restore Summary").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("• Reminders restored: 10").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysExportingProgress() {
        val state = SettingsUiState(
            isLoading = true,
            isExporting = true
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = state,
                    onAction = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Exporting...").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsContent_updatesThemeMode() {
        var actionReceived: SettingsAction? = null

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = SettingsUiState(),
                    onAction = { actionReceived = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Dark").performScrollTo().performClick()
        assertEquals(SettingsAction.UpdateThemeMode(com.example.offlinereminder.domain.model.ThemeMode.DARK), actionReceived)
    }

    @Test
    fun settingsContent_togglesTimeFormat() {
        var actionReceived: SettingsAction? = null

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = SettingsUiState(),
                    onAction = { actionReceived = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Use 24-Hour Format").performScrollTo().performClick()
        assertEquals(SettingsAction.UpdateTimeFormat(true), actionReceived)
    }

    @Test
    fun settingsContent_displaysCategoriesAndTriggersDelete() {
        var actionReceived: SettingsAction? = null
        val category = com.example.offlinereminder.domain.model.Category(id = "cat-custom", name = "Finance", isCustom = true)

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = SettingsUiState(categories = listOf(category)),
                    onAction = { actionReceived = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Finance").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete category").performScrollTo().performClick()
        assertEquals(SettingsAction.DeleteCategory("cat-custom"), actionReceived)
    }

    @Test
    fun settingsContent_displaysEventTypesAndTriggersDelete() {
        var actionReceived: SettingsAction? = null
        val eventType = com.example.offlinereminder.domain.model.EventType(
            id = "type-custom",
            name = "Standup",
            key = "standup",
            categoryId = "cat-1",
            createdAt = java.time.Instant.parse("2026-10-20T10:00:00Z")
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = SettingsUiState(eventTypes = listOf(eventType)),
                    onAction = { actionReceived = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Standup").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete event type").performScrollTo().performClick()
        assertEquals(SettingsAction.DeleteEventType("type-custom"), actionReceived)
    }

    @Test
    fun settingsContent_resetToDefaults_showsConfirmDialogAndDispatches() {
        var actionReceived: SettingsAction? = null

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = SettingsUiState(),
                    onAction = { actionReceived = it },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Reset All Settings to Defaults").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Reset All Settings?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset").performClick()
        assertEquals(SettingsAction.ResetSettings, actionReceived)
    }
}
