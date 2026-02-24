package io.sentry.seer.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeerRepoInfo(
    val provider: String,
    val owner: String,
    val name: String,
    @SerialName("external_id") val externalId: String,
    @SerialName("base_commit_sha") val baseCommitSha: String,
    @SerialName("organization_id") val organizationId: Int
)

@Serializable
data class SeerAnalysisRequest(
    val repo: SeerRepoInfo,
    val diff: String,
    @SerialName("organization_id") val organizationId: Int? = null,
    @SerialName("organization_slug") val organizationSlug: String? = null,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("commit_message") val commitMessage: String? = null,
    @SerialName("user_name") val userName: String? = null
)

@Serializable
data class SeerSubmitResponse(
    @SerialName("run_id") val runId: Int,
    val status: String
)

@Serializable
data class SeerPollResponse(
    val status: String,
    @SerialName("run_id") val runId: Int,
    val predictions: List<SeerPrediction>? = null,
    val diagnostics: SeerDiagnostics? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("is_draft") val isDraft: Boolean = false
)

@Serializable
data class SeerPrediction(
    val title: String,
    val description: String,
    @SerialName("short_description") val shortDescription: String,
    @SerialName("suggested_fix") val suggestedFix: String,
    @SerialName("encoded_location") val encodedLocation: String,
    val severity: String
)

@Serializable
data class SeerDiagnostics(
    @SerialName("files_analyzed") val filesAnalyzed: Int? = null,
    @SerialName("execution_time_seconds") val executionTimeSeconds: Double? = null
)

data class ParsedLocation(val filePath: String, val startLine: Int, val endLine: Int)

fun SeerPrediction.parseLocation(): ParsedLocation? {
    // Format: "path/to/file.kt:10" or "path/to/file.kt:10~20"
    val regex = Regex("""(.+):(\d+)(?:~(\d+))?""")
    val match = regex.matchEntire(encodedLocation) ?: return null
    val file = match.groupValues[1]
    val start = match.groupValues[2].toIntOrNull() ?: return null
    val end = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
    return ParsedLocation(file, start, end)
}
