package io.sentry.ui.profiling

import io.sentry.network.models.Project
import io.sentry.repository.SentryRepository
import io.sentry.settings.Settings
import io.sentry.ui.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.intellij.openapi.project.Project as IntellijProject

class ProfilingViewModel(
    private val intelliJProject: IntellijProject,
    private val repository: SentryRepository
) {

    private val viewModelScope: CoroutineScope = CoroutineScope(GlobalScope.coroutineContext)

    val projects = MutableStateFlow<UiState<List<Project>>>(UiState.Undefined)
    val selectedProject = MutableStateFlow<Project?>(null)
    val lineData = MutableStateFlow<UiState<Map<String, Long>>>(UiState.Undefined)

    init {
        viewModelScope.launch {
            repository.getProjects().collect { result ->
                projects.value = result.fold(
                    onSuccess = {
                        UiState.Success(
                            it
//                        listOf(Project("5428559", "sentry-android", "sentry-android", null, null, null,
//                            ProjectOrganization("0", "sentry-sdks", "sentry-sdks")))
                        )
                    },
                    onFailure = { UiState.Error(it) }
                )

                // Auto-select last used project if available
                result.onSuccess { projects ->
                    if (selectedProject.value == null && projects.isNotEmpty()) {
                        Settings.getLastUsedProjectId().collect { lastUsedProjectId ->
                            lastUsedProjectId?.let { projectId ->
                                projects.find { it.id == projectId }?.let { project ->
                                    onProjectSelected(project)
                                }
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            selectedProject.collect { project ->
//                if (project == null) {
//                    lineData.value = UiState.Undefined
//                } else {
//                    lineData.value = UiState.Loading
//                    repository.getProfiles(project.organization!!.slug, project.id).collect { result ->
//                        lineData.value = result.fold(
//                            onSuccess = {
//                                ProfilingDataService.getInstance(intelliJProject).loadProfilingData(it)
//                                UiState.Success(aggregateApplicationFrameCounts(it))
//                            },
//                            onFailure = { UiState.Error(it) }
//                        )
//                    }

//                }
            }
        }
    }

    fun onProjectSelected(project: Project) {
        selectedProject.value = project
    }


    fun dispose() {

    }
}