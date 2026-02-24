package io.sentry.seer.ui

import com.intellij.openapi.project.Project
import io.sentry.seer.SeerAnalysisService
import io.sentry.seer.SeerAnalysisState
import io.sentry.seer.SeerIdleTrigger
import io.sentry.seer.models.SeerPrediction
import io.sentry.seer.models.parseLocation
import io.sentry.ui.components.openFileInEditor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SeerViewModel(private val project: Project) {

    private val service = SeerAnalysisService.getInstance(project)
    private val idleTrigger = SeerIdleTrigger.getInstance(project)

    val state: StateFlow<SeerAnalysisState> = service.analysisState

    private val _autoAnalyze = MutableStateFlow(true)
    val autoAnalyze: StateFlow<Boolean> = _autoAnalyze

    init {
        // Auto-start idle trigger by default
        idleTrigger.start()
    }

    fun runAnalysis(baseBranch: String = "main") {
        service.startAnalysis(baseBranch)
    }

    fun cancel() {
        service.cancelAnalysis()
    }

    fun setAutoAnalyze(enabled: Boolean) {
        _autoAnalyze.value = enabled
        if (enabled) {
            idleTrigger.start()
        } else {
            idleTrigger.stop()
        }
    }

    fun onPredictionClicked(prediction: SeerPrediction) {
        val location = prediction.parseLocation() ?: return
        openFileInEditor(project, location.filePath, location.startLine)
    }

    fun dispose() {
        // Service is project-scoped, nothing extra to dispose
    }
}
