package org.example.events.analytics

sealed interface AnalyticsEvent {
    data class Login(
        val userId: String,
        val timestamp: Long
    ) : AnalyticsEvent

    data class ProductAnalytics(
        val userId: String,
        val productId: String,
        val timestamp: Long
    ) : AnalyticsEvent
}