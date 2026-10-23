package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.domain.statistics.EventStatistics
import com.example.offlinereminder.domain.statistics.InsightType
import com.example.offlinereminder.domain.statistics.ProductivityInsight
import java.util.UUID

class GetProductivityInsightsUseCase(
    private val getStatisticsUseCase: GetStatisticsUseCase
) {
    suspend operator fun invoke(): List<ProductivityInsight> {
        val stats = getStatisticsUseCase()
        return generateInsights(stats)
    }

    fun generateInsights(stats: EventStatistics): List<ProductivityInsight> {
        val insights = mutableListOf<ProductivityInsight>()

        // Guardrail: Minimum data threshold
        val totalActivity = stats.totalActiveEvents + stats.completedOccurrencesCount
        if (totalActivity < 3) {
            insights.add(
                ProductivityInsight(
                    id = "guardrail_starter",
                    type = InsightType.INFO,
                    title = "Building Your Habits",
                    description = "Add and complete a few more events to unlock personalized offline productivity insights.",
                    metricValue = "$totalActivity/3"
                )
            )
            return insights
        }

        // 1. Completion Rate Insight
        if (stats.completionRatePercentage >= 80f) {
            insights.add(
                ProductivityInsight(
                    id = "high_completion",
                    type = InsightType.ACHIEVEMENT,
                    title = "High Completion Rate",
                    description = "You're consistently achieving your goals with an outstanding ${stats.completionRatePercentage.toInt()}% completion rate.",
                    metricValue = "${stats.completionRatePercentage.toInt()}%"
                )
            )
        } else if (stats.completionRatePercentage < 50f && (stats.completedOccurrencesCount + stats.overdueOccurrencesCount) >= 4) {
            insights.add(
                ProductivityInsight(
                    id = "completion_boost",
                    type = InsightType.SUGGESTION,
                    title = "Agenda Catch-up",
                    description = "Consider rescheduling overdue tasks or spreading out high-priority items to regain momentum.",
                    metricValue = "${stats.completionRatePercentage.toInt()}%"
                )
            )
        }

        // 2. Overdue Warning
        if (stats.overdueOccurrencesCount > 0) {
            insights.add(
                ProductivityInsight(
                    id = "overdue_warning",
                    type = InsightType.WARNING,
                    title = "Overdue Items Pending",
                    description = "You have ${stats.overdueOccurrencesCount} item${if (stats.overdueOccurrencesCount > 1) "s" else ""} awaiting completion or rescheduling.",
                    metricValue = "${stats.overdueOccurrencesCount}"
                )
            )
        }

        // 3. Top Category Focus
        val topCategory = stats.categoryBreakdown.firstOrNull()
        if (topCategory != null && topCategory.count > 0) {
            insights.add(
                ProductivityInsight(
                    id = "top_category",
                    type = InsightType.INFO,
                    title = "Primary Focus Area",
                    description = "${topCategory.categoryName} represents ${topCategory.percentage.toInt()}% of your active schedule (${topCategory.count} items).",
                    metricValue = topCategory.categoryName
                )
            )
        }

        // 4. Peak Activity Day
        val peakDay = stats.dayOfWeekDistribution.maxByOrNull { it.value }
        if (peakDay != null && peakDay.value > 0) {
            val dayName = peakDay.key.name.lowercase().replaceFirstChar { it.uppercase() }
            insights.add(
                ProductivityInsight(
                    id = "peak_day",
                    type = InsightType.INFO,
                    title = "Peak Activity Day",
                    description = "$dayName is your most active day with ${peakDay.value} scheduled items.",
                    metricValue = dayName
                )
            )
        }

        // 5. On-Time Completion Performance
        if (stats.completedOccurrencesCount >= 3) {
            insights.add(
                ProductivityInsight(
                    id = "on_time_performance",
                    type = InsightType.ACHIEVEMENT,
                    title = "Punctuality Rating",
                    description = "${stats.onTimeCompletionRatePercentage.toInt()}% of your completed reminders were addressed on time.",
                    metricValue = "${stats.onTimeCompletionRatePercentage.toInt()}%"
                )
            )
        }

        return insights
    }
}
