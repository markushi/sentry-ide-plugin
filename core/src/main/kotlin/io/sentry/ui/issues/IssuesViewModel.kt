package io.sentry.ui.issues

import io.sentry.network.models.Issue
import io.sentry.network.models.Project
import io.sentry.repository.SentryRepository
import io.sentry.settings.Settings
import io.sentry.ui.UiState
import io.sentry.ui.components.StacktraceLine
import io.sentry.ui.components.openFileInEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.intellij.openapi.project.Project as IntellijProject

class IssuesViewModel(
    private val project: IntellijProject,
    private val repository: SentryRepository
) {

    private val viewModelScope: CoroutineScope = CoroutineScope(GlobalScope.coroutineContext)
    private val _uiState = MutableStateFlow(IssuesUiState())
    val uiState: StateFlow<IssuesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getProjects().collect { result ->
                val updatedState = _uiState.value.copy(
                    projects = result.fold(
                        onSuccess = {
                            UiState.Success(
                                it
                            )
                        },
                        onFailure = { UiState.Error(it) }
                    )
                )
                _uiState.value = updatedState

                // Auto-select last used project if available
                result.onSuccess { projects ->
                    if (updatedState.selectedProject == null && projects.isNotEmpty()) {
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
    }

    fun onProjectSelected(project: Project) {
        _uiState.value = _uiState.value.copy(
            selectedProject = project,
            issues = UiState.Loading
        )
        loadIssues(project)

        viewModelScope.launch {
            Settings.saveLastUsedProjectId(project.id)
        }
    }

    private fun loadIssues(project: Project) {
        viewModelScope.launch {
            repository.getOrgIssues(
                orgSlug = project.organization!!.slug,
                projectId = project.id
            ).collect { result ->
                _uiState.value = _uiState.value.copy(
                    issues = result.fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error(it) }
                    )
                )
            }
        }
    }

    fun onIssueSelected(issue: Issue) {
        val currentProject = _uiState.value.selectedProject ?: return

        _uiState.value = _uiState.value.copy(
            selectedIssue = issue,
            issueEvents = UiState.Loading
        )
        loadIssueEvents(currentProject, issue)
        loadIssueTags(currentProject, issue)
    }

    fun onIssueBackClicked() {
        _uiState.value = _uiState.value.copy(
            selectedIssue = null,
            issueEvents = UiState.Undefined,
            tagOverview = UiState.Undefined,
        )
    }

    private fun loadIssueEvents(project: Project, issue: Issue) {
        viewModelScope.launch {
            repository.getIssueEvents(
                orgSlug = project.organization!!.slug,
                issueId = issue.id
            ).collect { result ->
                _uiState.value = _uiState.value.copy(
                    issueEvents = result.fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error(it) }
                    )
                )
            }
        }
    }

    fun loadIssueTags(project: Project, issue: Issue) {
        _uiState.value = _uiState.value.copy(
            tagOverview = UiState.Loading
        )

        viewModelScope.launch {
            repository.getIssueTagOverview(
                orgSlug = project.organization!!.slug,
                issueId = issue.id,
            ).collect { result ->
                _uiState.value = _uiState.value.copy(
                    tagOverview = result.fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error(it) }
                    )
                )
            }
        }
    }

    fun onStacktraceLineClicked(line: StacktraceLine) {
        line.fileName?.let { file ->
            openFileInEditor(project, file, line.lineNumber)
        }
    }

    fun onQueryChanged(queryText: String) {
        _uiState.value = _uiState.value.copy(
            queryText = queryText
        )

        // Reload issues with the new query if a project is selected
        _uiState.value.selectedProject?.let { project ->
            _uiState.value = _uiState.value.copy(issues = UiState.Loading)
            loadIssues(project)
        }
    }

    fun dispose() {

    }
}