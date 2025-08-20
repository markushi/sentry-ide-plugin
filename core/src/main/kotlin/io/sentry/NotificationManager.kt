package io.sentry

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import io.sentry.network.models.Issue

class NotificationManager(private val project: Project) {

    companion object {
        private val NOTIFICATION_GROUP by lazy {
            NotificationGroupManager
                .getInstance()
                .getNotificationGroup("sentry.io")
        }
    }

    private val previousIssueIds = mutableSetOf<String>()

    fun onLiveIssuesReceived(
        currentIssues: List<Issue>,
        onIssueSelected: (Issue) -> Unit
    ) {
        val currentIssueIds = currentIssues.map { it.id }.toSet()
        val newIssueIds = currentIssueIds - previousIssueIds

        // only trigger after the first load
        if (previousIssueIds.isNotEmpty()) {
            val newIssues = currentIssues.filter { it.id in newIssueIds }
            newIssues.forEach { issue ->
                showNewIssueNotification(issue, onIssueSelected)
            }
        }

        previousIssueIds.clear()
        previousIssueIds.addAll(currentIssueIds)
    }

    private fun showNewIssueNotification(issue: Issue, onIssueSelected: (Issue) -> Unit) {
        val title = "New Sentry Issue Detected"
        val content = buildString {
            append(issue.title ?: "Unknown Issue")
            append("\n")
            append(issue.culprit ?: "Unknown Culprit")
        }

        val notification = NOTIFICATION_GROUP.createNotification(
            title,
            content,
            NotificationType.INFORMATION
        )

        notification.addAction(object : AnAction("View Issue") {
            override fun actionPerformed(e: AnActionEvent) {
                onIssueSelected(issue)
                notification.expire()
            }
        })

        notification.notify(project)
    }
}