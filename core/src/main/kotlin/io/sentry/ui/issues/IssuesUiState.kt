package io.sentry.ui.issues

import io.sentry.network.models.Event
import io.sentry.network.models.Issue
import io.sentry.network.models.Project
import io.sentry.network.models.TagOverview
import io.sentry.ui.UiState

data class IssuesUiState(
    val projects: UiState<List<Project>> = UiState.Loading,
    val selectedProject: Project? = null,
    val issues: UiState<List<Issue>> = UiState.Undefined,
    val selectedIssue: Issue? = null,
    val issueEvents: UiState<List<Event>> = UiState.Undefined,
    val tagOverview: UiState<List<TagOverview>> = UiState.Undefined,
    val queryText: String = ""
)