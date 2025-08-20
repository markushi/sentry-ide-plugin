package io.sentry.ui.livedebug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.sentry.network.models.Issue
import io.sentry.network.models.Organization
import io.sentry.ui.Colors
import io.sentry.ui.UiState
import io.sentry.ui.issues.IssueDetails
import io.sentry.ui.issues.IssuesListView
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

@Composable
fun LiveDebug(viewModel: LiveDebugViewModel) {
    val devices = viewModel.connectedDevices.collectAsState()
    val deviceCount = devices.value.count()

    val organizations: State<UiState<List<Organization>>> = viewModel.organizations.collectAsState()
    val selectedOrganization: State<Organization?> = viewModel.selectedOrganization.collectAsState()
    val issuePollingState = viewModel.issueState.collectAsState()
    val issues = viewModel.allIssues.collectAsState()
    val selectedIssue = viewModel.selectedIssue.collectAsState()
    val selectedIssueEvents = viewModel.selectedIssueEvents.collectAsState()

    val issue = selectedIssue.value
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (issue == null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OrganizationSelector(
                    organizations.value,
                    selectedOrganization.value
                ) { organization ->
                    viewModel.onOrganizationSelected(organization)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = (Arrangement.End)
                ) {
                    DeviceStatus(deviceCount)
                    Spacer(modifier = Modifier.size(8.dp))
                    IssueStatus(
                        modifier = Modifier.clickable {
                            viewModel.pollForIssues()
                        },
                        pollingState = issuePollingState.value,
                        issues = issues.value
                    )
                }
            }
            IssuesListView(issues.value) { issue ->
                viewModel.onIssueSelected(issue)
            }
        } else {
            IssueDetails(
                issue, selectedIssueEvents.value, onBackClicked = {
                    viewModel.onIssueBackClicked()
                },
                onStackTraceLineClicked = { line ->
                    viewModel.onStacktraceLineClicked(line)
                })
        }
    }
}

@Composable
private fun IssueStatus(
    pollingState: UiState<Unit>,
    issues: UiState<List<Issue>>,
    modifier: Modifier = Modifier
) {
    val issueStatusColor = when (pollingState) {
        is UiState.Undefined -> Colors.STATUS_NEUTRAL
        is UiState.Loading -> Colors.STATUS_PENDING
        is UiState.Success -> Colors.STATUS_OK
        is UiState.Error -> Colors.STATUS_ERROR
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = issueStatusColor, shape = CircleShape)
        )
        val count = if (issues is UiState.Success) issues.data.count() else 0
        Text(text = "$count issues discovered")
    }
}

@Composable
private fun DeviceStatus(deviceCount: Int, modifier: Modifier = Modifier) {
    val deviceStatusColor = when {
        (deviceCount > 0) -> Colors.STATUS_OK
        else -> Colors.STATUS_NEUTRAL
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = deviceStatusColor, shape = CircleShape)
        )
        Text(text = "Connected devices: $deviceCount")
    }
}

@Composable
private fun OrganizationSelector(
    availableOrganizations: UiState<List<Organization>>,
    selectedOrganization: Organization?,
    onOrganizationSelected: (organization: Organization) -> Unit
) {
    when (availableOrganizations) {
        is UiState.Loading -> Text("Loading")
        is UiState.Success -> {
            Dropdown(modifier = Modifier.defaultMinSize(minWidth = 120.dp), menuContent = {
                availableOrganizations.data.forEach { organization ->
                    selectableItem(selectedOrganization == organization, onClick = {
                        onOrganizationSelected(organization)
                    }, content = {
                        Text(text = organization.slug)
                    })
                }
            }, content = {
                Text(
                    text = selectedOrganization?.slug ?: "Choose an org...", maxLines = 2
                )
            })
        }

        else -> {}
    }
}