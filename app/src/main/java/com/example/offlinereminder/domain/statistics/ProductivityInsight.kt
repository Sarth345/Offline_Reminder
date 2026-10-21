package com.example.offlinereminder.domain.statistics

enum class InsightType {
    ACHIEVEMENT,
    SUGGESTION,
    WARNING,
    INFO
}

/**
 * Local productivity insight generated deterministically from event history and statistics.
 */
data class ProductivityInsight(
    val id: String,
    val type: InsightType,
    val title: String,
    val description: String,
    val metricValue: String? = null
)
