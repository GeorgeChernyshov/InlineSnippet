package org.example.events.enrichedanalytics

sealed interface EnrichedAnalyticsEvent {
    data class Login(
        val userId: String,
        val timestamp: Long,
        val userType: String
    ) : EnrichedAnalyticsEvent

    data class ProductEnriched(
        val userId: String,
        val productId: String,
        val timestamp: Long,
        val category: String
    ) : EnrichedAnalyticsEvent
}