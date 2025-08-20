package io.sentry.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseStatsResponse(
    val data: List<ReleaseStats>,
    val meta: StatsMetadata,
    val confidence: List<Map<String, String>>? = null
)

@Serializable
data class ReleaseStats(
    val release: String,
    @SerialName("count()")
    val count: Double
)

@Serializable
data class StatsMetadata(
    val fields: Map<String, String>,
    val units: Map<String, String?>,
    val isMetricsData: Boolean = false,
    val isMetricsExtractedData: Boolean = false,
    val tips: Map<String, String> = emptyMap(),
    val datasetReason: String? = null,
    val dataset: String,
    val dataScanned: String? = null,
    val accuracy: StatsAccuracy? = null
)

@Serializable
data class StatsAccuracy(
    val confidence: List<Map<String, String>>? = null
)