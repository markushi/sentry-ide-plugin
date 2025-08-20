package io.sentry.network.models

import kotlinx.serialization.Serializable

@Serializable
data class Release(
    val id: Long,
    val version: String,
    val shortVersion: String? = null,
    val dateCreated: String,
    val dateReleased: String? = null,
    val firstEvent: String? = null,
    val lastEvent: String? = null,
    val commitCount: Int = 0,
    val authors: List<ReleaseAuthor> = emptyList(),
    val projects: List<ReleaseProject> = emptyList()
)

@Serializable
data class ReleaseAuthor(
    val name: String,
    val email: String? = null
)

@Serializable
data class ReleaseProject(
    val name: String,
    val slug: String,
    val id: String? = null
)