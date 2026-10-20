package com.example.offlinereminder.domain.backup

/**
 * Summary metrics describing the outcome of a successful database restore.
 */
data class RestoreSummary(
    val eventsRestored: Int = 0,
    val categoriesRestored: Int = 0,
    val groupsRestored: Int = 0,
    val eventTypesRestored: Int = 0,
    val tagsRestored: Int = 0,
    val exceptionsRestored: Int = 0,
    val dependenciesRestored: Int = 0,
    val relationshipsRestored: Int = 0,
    val attachmentsRestored: Int = 0
) {
    val totalRecordsRestored: Int
        get() = eventsRestored + categoriesRestored + groupsRestored + eventTypesRestored +
                tagsRestored + exceptionsRestored + dependenciesRestored + relationshipsRestored
}
