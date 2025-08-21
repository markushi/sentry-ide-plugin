package io.sentry.network.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.sentry.network.models.*
import io.sentry.profiling.Profiles

class SentryApiClient(token: String, private val baseUrl: String = "https://sentry.io/api/0") {

    companion object {
        private const val TAG = "SentryApiClient"
    }

    private val httpClient: HttpClient = SentryHttpClient.create(token)

    suspend fun getOrganizations(): List<Organization> {
        return try {
            httpClient.get("$baseUrl/organizations/").body<List<Organization>>()
        } catch (e: Exception) {
            throw toSentryApiException("Failed to fetch organizations", e)
        }
    }

    suspend fun getProjects(): List<Project> {
        return try {
            val response = httpClient.get("$baseUrl/projects/")
            val result = response.body<List<Project>>()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            throw toSentryApiException("Failed to fetch projects", e)
        }
    }

    suspend fun getOrganizationProjects(orgSlug: String): List<Project> {
        return try {
            httpClient.get("$baseUrl/organizations/$orgSlug/projects/").body<List<Project>>()
        } catch (e: Exception) {
            throw toSentryApiException("Failed to fetch projects for organization $orgSlug", e)
        }
    }

    suspend fun getProjectReleases(
        orgSlug: String, projectId: String, perPage: Int = 50, sort: String = "sessions", flatten: Boolean = true
    ): List<Release> {
        return try {
            val url =
                "$baseUrl/organizations/$orgSlug/releases/" + "?flatten=${if (flatten) 1 else 0}" + "&per_page=$perPage" + "&project=$projectId" + "&sort=$sort"
            httpClient.get(url).body<List<Release>>()
        } catch (e: Exception) {
            throw toSentryApiException("Failed to fetch releases for project $projectId in organization $orgSlug", e)
        }
    }

    suspend fun getReleaseStatistics(
        orgSlug: String,
        projectId: String,
        releases: List<String>,
        dataset: String = "spans",
        fields: List<String> = listOf("release", "count()"),
        statsPeriod: String = "14d",
        perPage: Int = 50
    ): ReleaseStatsResponse {
        return try {
            val releaseFilter = releases.joinToString(",") { "\"$it\"" }
            val query = "release:[$releaseFilter]"

            httpClient.get("$baseUrl/organizations/$orgSlug/events/") {
                parameter("dataset", dataset)
                fields.forEach { field ->
                    parameter("field", field)
                }
                parameter("per_page", perPage)
                parameter("project", projectId)
                parameter("query", query)
                parameter("referrer", "api.insights.mobile-release-selector")
                parameter("statsPeriod", statsPeriod)
            }.body<ReleaseStatsResponse>()
        } catch (e: Exception) {
            throw toSentryApiException(
                "Failed to fetch release statistics for project $projectId in organization $orgSlug", e
            )
        }
    }

    suspend fun getOrgIssues(
        orgSlug: String,
        projectId: String?,
        query: String? = null,
        statsPeriod: String? = null,
        shortIdLookup: Boolean? = null,
        sort: String = "date",
        perPage: Int = 25
    ): List<Issue> {
        return try {
            val response = httpClient.get("$baseUrl/organizations/$orgSlug/issues/") {
                projectId?.let { parameter("project", it) }
                parameter("sort", sort)
                parameter("per_page", perPage)
                query?.let { parameter("query", it) }
                statsPeriod?.let { parameter("statsPeriod", it) }
                shortIdLookup?.let { parameter("shortIdLookup", if (it) "1" else "0") }
            }
            response.body<List<Issue>>()
        } catch (e: Exception) {
            throw toSentryApiException("Failed to fetch issues for project $projectId in organization $orgSlug", e)
        }
    }

    suspend fun getIssueEvents(
        orgSlug: String,
        issueId: String,
        start: String? = null,
        end: String? = null,
        statsPeriod: String? = null,
        environment: List<String>? = null,
        full: Boolean? = null,
        sample: Boolean? = null,
        query: String? = null
    ): List<Event> {
        return try {
            val response = httpClient.get("$baseUrl/organizations/$orgSlug/issues/$issueId/events/") {
                start?.let { parameter("start", it) }
                end?.let { parameter("end", it) }
                statsPeriod?.let { parameter("statsPeriod", it) }
                environment?.forEach { parameter("environment", it) }
                full?.let { parameter("full", it) }
                sample?.let { parameter("sample", it) }
                query?.let { parameter("query", it) }
            }
            response.body<List<Event>>()
        } catch (e: Exception) {
            throw toSentryApiException("Failed to fetch events for issue $issueId in organization $orgSlug", e)
        }
    }

    suspend fun getIssueTagOverview(
        orgSlug: String,
        issueId: String
    ): List<TagOverview> {
        return try {
            val response = httpClient.get("$baseUrl/organizations/$orgSlug/issues/$issueId/tags/")
            response.body<List<TagOverview>>()
        } catch (e: Exception) {
            throw toSentryApiException("Failed to fetch tag details on issue $issueId in organization $orgSlug", e)
        }
    }

    suspend fun getProfiles(
        orgSlug: String,
        projectId: String,
    ): Profiles {
        return try {
            val response = httpClient.get(
                "$baseUrl/organizations/$orgSlug/profiling/flamegraph/?dataSource=profiles&project=$projectId&statsPeriod=7d"
            )
            response.body<Profiles>()
        } catch (e: Exception) {
            throw toSentryApiException("Failed to fetch profiles in organization $orgSlug/$projectId", e)
        }
    }

    fun close() {
        httpClient.close()
    }
}