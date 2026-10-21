package com.example.offlinereminder.presentation.templates

sealed interface TemplatesAction {
    data object LoadTemplates : TemplatesAction
    data class CreateEventFromTemplate(val templateId: String) : TemplatesAction
    data class DeleteTemplate(val templateId: String) : TemplatesAction
    data object ClearMessage : TemplatesAction
    data object ClearCreatedEvent : TemplatesAction
}
