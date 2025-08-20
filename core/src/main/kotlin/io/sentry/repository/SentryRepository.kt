package io.sentry.repository

import io.sentry.logging.Logger
import io.sentry.network.client.SentryApiClient
import io.sentry.network.models.*
import io.sentry.settings.Settings
import kotlinx.coroutines.flow.*

class SentryRepository(
    private val apiClient: SentryApiClient = SentryApiClient(System.getenv("SENTRY_TOKEN")),
    private val cacheOnly: Boolean = false
) {
    companion object {
        private const val TAG = "SentryRepository"
    }

    fun getProjects(): Flow<Result<List<Project>>> = flow {
        try {
            if (!cacheOnly) {
                val projects = apiClient.getProjects()
                Settings.updateCachedProjects(projects)
                Logger.debug(TAG, "Emitting new projects")
                emit(Result.success(projects))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to load projects", e)
            emit(Result.failure(e))
        }
    }.onStart {
        // First emit cached data if available
        val cachedProjects = Settings.getCachedProjects().first()
        if (cachedProjects.isNotEmpty()) {
            Logger.debug(TAG, "Emitting cached projects")
            emit(Result.success(cachedProjects))
        }
    }.catch { e ->
        emit(Result.failure(e))
    }

    fun getOrganizations(): Flow<Result<List<Organization>>> = flow {
        try {
            val orgs = apiClient.getOrganizations()
            emit(Result.success(orgs))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    fun getOrgIssues(
        orgSlug: String, projectId: String? = null, query: String? = null, statsPeriod: String? = null
    ): Flow<Result<List<Issue>>> = flow {
        try {
            if (!cacheOnly) {
                val issues = apiClient.getOrgIssues(
                    orgSlug = orgSlug, projectId = projectId, query = query, statsPeriod = statsPeriod
                )
                Logger.debug(TAG, "Emitting new issues")
                emit(Result.success(issues))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to load issues", e)
            emit(Result.failure(e))
        }
    }.catch { e ->
        emit(Result.failure(e))
    }

    fun getIssueEvents(
        orgSlug: String, issueId: String, statsPeriod: String? = null
    ): Flow<Result<List<Event>>> = flow {
        try {
            if (!cacheOnly) {
                val events = apiClient.getIssueEvents(
                    orgSlug = orgSlug, issueId = issueId, statsPeriod = statsPeriod, full = true
                )
                Settings.updateCachedEvents(issueId, events)
                Logger.debug(TAG, "Emitting new events for issue $issueId")
                emit(Result.success(events))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to load events for issue $issueId", e)
            emit(Result.failure(e))
        }
    }.onStart {
        val cachedEvents = Settings.getCachedEvents(issueId).first()
        if (cachedEvents.isNotEmpty()) {
            Logger.debug(TAG, "Emitting cached events for issue $issueId")
            emit(Result.success(cachedEvents))
        }
    }.catch { e ->
        emit(Result.failure(e))
    }

    suspend fun getIssueTagOverview(
        orgSlug: String, issueId: String
    ): Flow<Result<List<TagOverview>>> = flow {
        try {
            if (cacheOnly) {
                emit(Result.failure(Exception("Cache-only mode enabled, cannot fetch tag details")))
            } else {
                val tagDetails = apiClient.getIssueTagOverview(
                    orgSlug = orgSlug, issueId = issueId
                )
                Logger.debug(TAG, "Retrieved tag details on issue $issueId")
                Settings.updateCachedTags(issueId, tagDetails)
                emit(Result.success(tagDetails))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to load tag details on issue $issueId", e)
            emit(Result.failure(e))
        }
    }.onStart {
        val cachedTags = Settings.getCachedTags(issueId).first()
        if (cachedTags.isNotEmpty()) {
            Logger.debug(TAG, "Emitting cached tags for issue $issueId")
            emit(Result.success(cachedTags))
        }
    }.catch { e ->
        emit(Result.failure(e))
    }
}