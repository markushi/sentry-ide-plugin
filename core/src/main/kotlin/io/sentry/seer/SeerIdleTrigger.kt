package io.sentry.seer

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*

@Service(Service.Level.PROJECT)
class SeerIdleTrigger(private val project: Project) : Disposable {

    companion object {
        private val LOG = Logger.getInstance(SeerIdleTrigger::class.java)
        private const val IDLE_DELAY_MS = 5_000L

        fun getInstance(project: Project): SeerIdleTrigger {
            return project.getService(SeerIdleTrigger::class.java)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceJob: Job? = null
    private var enabled = false
    private var documentListener: DocumentListener? = null

    fun start() {
        if (enabled) return
        enabled = true

        val listener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                onDocumentChanged()
            }
        }
        documentListener = listener
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, this)
        LOG.info("Idle trigger started")
    }

    fun stop() {
        enabled = false
        debounceJob?.cancel()
        LOG.info("Idle trigger stopped")
    }

    fun isEnabled(): Boolean = enabled

    private fun onDocumentChanged() {
        if (!enabled) return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(IDLE_DELAY_MS)
            triggerAnalysisIfNeeded()
        }
    }

    private fun triggerAnalysisIfNeeded() {
        val service = SeerAnalysisService.getInstance(project)
        if (!service.isIdle()) {
            LOG.info("Analysis already running, skipping idle trigger")
            return
        }
        if (service.hasFreshResults()) {
            LOG.info("Results still fresh, skipping idle trigger")
            return
        }
        LOG.info("Idle timeout reached, triggering Seer analysis")
        service.startAnalysis()
    }

    override fun dispose() {
        enabled = false
        scope.cancel()
    }
}
