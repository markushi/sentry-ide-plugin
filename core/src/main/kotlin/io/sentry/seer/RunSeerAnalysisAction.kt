package io.sentry.seer

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class RunSeerAnalysisAction : AnAction("Run Seer Bug Prediction") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SeerAnalysisService.getInstance(project).startAnalysis()
    }
}
