package io.sentry.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Issue(
    val id: String,
    val shortId: String,
    val title: String,
    val culprit: String? = null,
    val permalink: String? = null,
    val logger: String? = null,
    val level: String? = null,
    val status: String? = null,
    val platform: String? = null,
    val type: String? = null,
    val issueType: String? = null,
    val issueCategory: String? = null,
    val priority: String? = null,
    val priorityLockedAt: String? = null,
    val isUnhandled: Boolean? = null,
    val count: String? = null,
    val userCount: Int? = null,
    val firstSeen: String? = null,
    val lastSeen: String? = null,
    val stats: Stats? = null
)

@Serializable
data class Stats(
    @SerialName("24h")
    val last24Hours: List<List<Int>>? = null
)

fun Int.formatCompact(): String {
    return when {
        this < 1000 -> this.toString()
        this < 1000000 -> String.format("%.1fk", this / 1000.0).replace(".0", "")
        this < 1000000000 -> String.format("%.1fM", this / 1000000.0).replace(".0", "")
        else -> String.format("%.1fB", this / 1000000000.0).replace(".0", "")
    }
}

@Serializable
data class IssueProject(
    val id: String,
    val name: String,
    val slug: String,
    val platform: String? = null
)