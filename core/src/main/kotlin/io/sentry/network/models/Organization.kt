package io.sentry.network.models

import kotlinx.serialization.Serializable

@Serializable
data class Organization(
    val id: String,
    val slug: String,
    val name: String,
    val dateCreated: String? = null,
    val status: OrganizationStatus? = null
)

@Serializable
data class OrganizationStatus(
    val id: String,
    val name: String
)