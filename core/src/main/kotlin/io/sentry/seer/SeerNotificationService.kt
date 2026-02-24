package io.sentry.seer

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import io.sentry.seer.models.SeerPrediction

object SeerNotificationService {

    private val NOTIFICATION_GROUP by lazy {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("sentry.io")
    }

    fun notifyCompleted(project: Project, predictions: List<SeerPrediction>) {
        val title = "Seer Bug Prediction"
        val content = if (predictions.isEmpty()) {
            "No issues found in your changes."
        } else {
            "Found ${predictions.size} potential issue(s) in your changes."
        }

        val type = if (predictions.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING

        val notification = NOTIFICATION_GROUP.createNotification(title, content, type)

        if (predictions.isNotEmpty()) {
            notification.addAction(object : AnAction("View Results") {
                override fun actionPerformed(e: AnActionEvent) {
                    activateSeerTab(project)
                    notification.expire()
                }
            })
        }

        notification.notify(project)
    }

    fun notifyError(project: Project, message: String) {
        val notification = NOTIFICATION_GROUP.createNotification(
            "Seer Bug Prediction",
            "Analysis failed: $message",
            NotificationType.ERROR
        )

        notification.addAction(object : AnAction("View Details") {
            override fun actionPerformed(e: AnActionEvent) {
                activateSeerTab(project)
                notification.expire()
            }
        })

        notification.notify(project)
    }

    private fun activateSeerTab(project: Project) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("sentry-ide-panel")
        toolWindow?.let { tw ->
            tw.activate {
                // Select the Seer tab (last tab)
                val contents = tw.contentManager.contents
                val seerContent = contents.find { it.displayName == "Seer" }
                if (seerContent != null) {
                    tw.contentManager.setSelectedContent(seerContent)
                }
            }
        }
    }
}
