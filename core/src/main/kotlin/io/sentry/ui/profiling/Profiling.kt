package io.sentry.ui.profiling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.sentry.ui.Colors
import io.sentry.ui.UiState
import io.sentry.ui.issues.ProjectSelector
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.jewel.ui.component.Text

@Composable
fun Profiling(profilingViewModel: ProfilingViewModel) {
    val projects = profilingViewModel.projects.asStateFlow()
    val selectedProject = profilingViewModel.selectedProject.asStateFlow()
    val dataState = profilingViewModel.lineData.asStateFlow()
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        ProjectSelector(projects.value, selectedProject.value) { project ->
            profilingViewModel.onProjectSelected(project)
        }

        val statusColor = when (dataState.value) {
            is UiState.Loading -> Colors.STATUS_PENDING
            is UiState.Error -> Colors.STATUS_ERROR
            is UiState.Success -> Colors.STATUS_OK
            else -> Colors.STATUS_NEUTRAL
        }
        val statusText = when (dataState.value) {
            is UiState.Loading -> "Loading"
            is UiState.Error -> "Error: " + (dataState.value as UiState.Error).exception.message
            is UiState.Success -> "Line Data Loaded: ${(dataState.value as UiState.Success<Map<String, Long>>).data.size} items."
            else -> ""
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = statusColor, shape = CircleShape)
            )
            Text(text = statusText)
        }
    }
}