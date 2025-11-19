package org.example.events

sealed interface AppEvent
data class UserLoggedIn(val userId: String) : AppEvent
data class ProductViewed(val userId: String, val productId: String) : AppEvent
data class DataUpdated(val key: String, val value: Any) : AppEvent
data class UserLoggedOut(val userId: String) : AppEvent