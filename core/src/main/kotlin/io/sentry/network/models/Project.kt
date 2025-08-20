package io.sentry.network.models

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val slug: String,
    val name: String,
    val platform: String? = null,
    val dateCreated: String? = null,
    val status: String? = null,
    val organization: ProjectOrganization? = null
) {
    fun asSlug(): String = organization?.let { org ->
        "${org.slug}/$slug"
    } ?: slug
}

@Serializable
data class ProjectOrganization(
    val id: String,
    val slug: String,
    val name: String
)