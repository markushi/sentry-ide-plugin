package io.sentry.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.sentry.logging.Logger
import io.sentry.network.models.Event
import io.sentry.network.models.Issue
import io.sentry.network.models.Project
import io.sentry.network.models.TagOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import java.io.File

object Settings {

    val CACHED_PROJECTS = stringPreferencesKey("cached.projects")
    val LAST_USED_PROJECT_ID = stringPreferencesKey("last.used.project.id")
    private const val TAG = "Settings"

    private fun cachedIssuesKey(projectId: String) = stringPreferencesKey("cached.issues.$projectId")
    private fun cachedEventsKey(issueId: String) = stringPreferencesKey("cached.events.$issueId")
    private fun cachedTagsKey(issueId: String) = stringPreferencesKey("cached.tags.$issueId")

    private val DATA_STORE: DataStore<Preferences> by lazy { createDataStore() }

    suspend fun updateCachedProjects(projects: List<Project>) {
        DATA_STORE.edit { prefs ->
            prefs[CACHED_PROJECTS] = Json.encodeToString(projects)
        }
    }

    suspend fun updateCachedIssues(projectId: String, issues: List<Issue>) {
        DATA_STORE.edit { prefs ->
            prefs[cachedIssuesKey(projectId)] = Json.encodeToString(issues)
        }
    }

    suspend fun updateCachedEvents(issueId: String, events: List<Event>) {
        DATA_STORE.edit { prefs ->
            prefs[cachedEventsKey(issueId)] = Json.encodeToString(events)
        }
    }

    suspend fun updateCachedTags(issueId: String, tags: List<TagOverview>) {
        DATA_STORE.edit { prefs ->
            prefs[cachedTagsKey(issueId)] = Json.encodeToString(tags)
        }
    }

    suspend fun saveLastUsedProjectId(projectId: String) {
        DATA_STORE.edit { prefs ->
            prefs[LAST_USED_PROJECT_ID] = projectId
        }
    }

    fun getCachedProjects(): Flow<List<Project>> {
        return DATA_STORE.data.map { prefs ->
            prefs[CACHED_PROJECTS]?.let {
                try {
                    return@map Json.decodeFromString<List<Project>>(it)
                } catch (e: Exception) {
                    Logger.error(TAG, "Failed to decode cached projects", e)
                }
            }
            emptyList()
        }
    }

    fun getCachedIssues(projectId: String): Flow<List<Issue>> {
        return DATA_STORE.data.map { prefs ->
            prefs[cachedIssuesKey(projectId)]?.let {
                try {
                    return@map Json.decodeFromString<List<Issue>>(it)
                } catch (e: Exception) {
                    Logger.error(TAG, "Failed to decode cached issues for project $projectId", e)
                }
            }
            emptyList()
        }
    }

    fun getCachedEvents(issueId: String): Flow<List<Event>> {
        return DATA_STORE.data.map { prefs ->
            prefs[cachedEventsKey(issueId)]?.let {
                try {
                    return@map Json.decodeFromString<List<Event>>(it)
                } catch (e: Exception) {
                    Logger.error(TAG, "Failed to decode cached events for issue $issueId", e)
                }
            }
            emptyList()
        }
    }

    fun getCachedTags(issueId: String): Flow<List<TagOverview>> {
        return DATA_STORE.data.map { prefs ->
            prefs[cachedTagsKey(issueId)]?.let {
                try {
                    return@map Json.decodeFromString<List<TagOverview>>(it)
                } catch (e: Exception) {
                    Logger.error(TAG, "Failed to decode cached events for issue $issueId", e)
                }
            }
            emptyList()
        }
    }

    fun getLastUsedProjectId(): Flow<String?> {
        return DATA_STORE.data.map { prefs ->
            prefs[LAST_USED_PROJECT_ID]
        }
    }
}

fun createDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    produceFile = {
        val file = File(dataStoreFileName)
        file.toOkioPath()
    }
)

internal const val dataStoreFileName = "settings.preferences_pb"