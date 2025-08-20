package io.sentry.network.models

import kotlinx.serialization.Serializable

@Serializable
data class TagOverview(
    val key: String,
    val name: String,
    val totalValues: Int,
    val topValues: List<TagValue>
)

@Serializable
data class TagDetails(
    val key: String,
    val name: String,
    val uniqueValues: Int,
    val totalValues: Int,
    val topValues: List<TagValue>
)

@Serializable
data class TagValue(
    val key: String,
    val name: String,
    val value: String,
    val count: Int,
    val lastSeen: String,
    val firstSeen: String
)