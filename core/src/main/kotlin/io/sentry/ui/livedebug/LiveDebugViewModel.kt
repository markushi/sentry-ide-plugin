package io.sentry.ui.livedebug

import com.intellij.openapi.project.Project
import io.sentry.NotificationManager
import io.sentry.adb.DeviceListWatcher
import io.sentry.adb.SentryLogListener
import io.sentry.logging.Logger
import io.sentry.network.models.Event
import io.sentry.network.models.Issue
import io.sentry.network.models.Organization
import io.sentry.repository.SentryRepository
import io.sentry.ui.UiState
import io.sentry.ui.components.StacktraceLine
import io.sentry.ui.components.openFileInEditor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class LiveDebugViewModel(
    private val project: Project,
    private val repo: SentryRepository,
    private val notificationManager: NotificationManager,
    private val makeActive: () -> Unit
) {

    companion object {
        private const val POLLING_INTERVAL: Long = 10_000
        private const val TAG = "LiveDebugViewModel"
    }

    val connectedDevices = MutableStateFlow(mutableSetOf<String>())
    val organizations = MutableStateFlow<UiState<List<Organization>>>(UiState.Undefined)
    val selectedOrganization = MutableStateFlow<Organization?>(null)
    val sentryInstallationIds = MutableStateFlow(mutableSetOf<String>())

    val allIssues = MutableStateFlow<UiState<List<Issue>>>(UiState.Undefined)
    val selectedIssue = MutableStateFlow<Issue?>(null)
    val selectedIssueEvents = MutableStateFlow<UiState<List<Event>>>(UiState.Undefined)
    val issueState = MutableStateFlow<UiState<Unit>>(UiState.Loading)

    private val viewModelScope: CoroutineScope = CoroutineScope(GlobalScope.coroutineContext)
    var pollingJob: Job? = null

    val deviceListWatcher = DeviceListWatcher(object : SentryLogListener {
        override fun onSentryDeviceIdFound(deviceId: String, installationId: String) {
            sentryInstallationIds.value.add(installationId)
            pollForIssues()
        }

        override fun onDeviceConnected(deviceId: String) {
            connectedDevices.value.add(deviceId)
        }

        override fun onDeviceDisconnected(deviceId: String) {
            connectedDevices.value.remove(deviceId)
        }
    })

    init {
        deviceListWatcher.start()
        Runtime.getRuntime().addShutdownHook(Thread {
            deviceListWatcher.stopWatching()
        })
        viewModelScope.launch {
            repo.getOrganizations().collect { result ->
                organizations.value = result.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) }
                )
                // pre-select org
                if (selectedOrganization.value == null && organizations.value is UiState.Success) {
                    selectedOrganization.value =
                        (organizations.value as UiState.Success<List<Organization>>).data.firstOrNull()
                }
            }
        }

        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            try {
                while (true) {
                    delay(POLLING_INTERVAL)
                    pollForIssues()
                }
            } catch (_: Exception) {

            }
        }

        viewModelScope.launch {
            selectedOrganization.collect {
                pollForIssues()
            }
        }
        viewModelScope.launch {
            selectedIssue.collect {
                selectedIssueEvents.value = UiState.Undefined
                selectedOrganization.value?.let { org ->
                    it?.let { issue ->
                        selectedIssueEvents.value = UiState.Loading
                        repo.getIssueEvents(org.slug, issue.id).collect { result ->
                            result.fold(
                                onSuccess = { issues ->
                                    selectedIssueEvents.value = UiState.Success(issues)
                                }, { exception ->
                                    selectedIssueEvents.value = UiState.Error(exception)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    fun pollForIssues() {
        Logger.debug(TAG, "polling for issues")

        val installationIds = sentryInstallationIds.value
        val org = selectedOrganization.value
        if (installationIds.isEmpty() || org == null) {
            issueState.value = UiState.Undefined
        } else {
            // format: user:"id:<id>" user:"id:<id>"
            val query = installationIds.joinToString(" ") {
                "user:\"id:${installationIds.first()}\""
            }
            issueState.value = UiState.Loading
            viewModelScope.launch {
                repo.getOrgIssues(org.slug, query = query).collect { result ->
                    result.fold(
                        onSuccess = { issues ->
                            issueState.value = UiState.Success(Unit)
                            allIssues.value = UiState.Success(issues)
                            notificationManager.onLiveIssuesReceived(issues) { issue ->
                                makeActive()
                                selectedIssue.value = issue
                            }
                        }, { exception ->
                            issueState.value = UiState.Error(exception)
                        }
                    )
                }
            }
        }
    }

    fun onOrganizationSelected(organization: Organization) {
        selectedOrganization.value = organization
        pollForIssues()
    }

    fun onIssueSelected(issue: Issue) {
        selectedIssue.value = issue
    }

    fun onStacktraceLineClicked(line: StacktraceLine) {
        line.fileName?.let { file ->
            openFileInEditor(project, file, line.lineNumber)
        }
    }

    fun dispose() {
        deviceListWatcher.stopWatching()
        pollingJob?.cancel()
    }

    fun onIssueBackClicked() {
        selectedIssue.value = null
        selectedIssueEvents.value = UiState.Undefined
    }

}