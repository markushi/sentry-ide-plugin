package io.sentry.seer

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.sentry.seer.git.GitHubUtils
import io.sentry.seer.git.GitUtils
import io.sentry.seer.models.*
import io.sentry.seer.network.SeerApiClient
import io.sentry.seer.editor.SeerAnnotationService
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

sealed class SeerAnalysisState {
    object Idle : SeerAnalysisState()
    data class Submitting(val fileCount: Int) : SeerAnalysisState()
    data class Polling(val runId: Int, val status: String) : SeerAnalysisState()
    data class Completed(
        val predictions: List<SeerPrediction>,
        val diagnostics: SeerDiagnostics?,
        val isDraft: Boolean = false
    ) : SeerAnalysisState()

    data class Error(val message: String) : SeerAnalysisState()
}

@Service(Service.Level.PROJECT)
class SeerAnalysisService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(SeerAnalysisService::class.java)
        private const val POLL_INTERVAL_MS = 3_000L
        private const val POLL_TIMEOUT_MS = 10 * 60 * 1_000L
        private const val MAX_DIFF_FILES = 50
        private const val MAX_DIFF_SIZE_BYTES = 500 * 1024

        fun getInstance(project: Project): SeerAnalysisService {
            return project.getService(SeerAnalysisService::class.java)
        }
    }

    private val apiClient = SeerApiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private var lastDiffHash: Int? = null

    // Set to true to skip the API and return fake predictions instantly
    private val useMockData = false

    val analysisState = MutableStateFlow<SeerAnalysisState>(SeerAnalysisState.Idle)

    init {
        scope.launch {
            analysisState.collect { state ->
                when (state) {
                    is SeerAnalysisState.Completed -> {
                        if (!state.isDraft) {
                            SeerNotificationService.notifyCompleted(project, state.predictions)
                        }
                        SeerAnnotationService.getInstance(project).applyPredictions(state.predictions)
                    }
                    is SeerAnalysisState.Error -> {
                        SeerNotificationService.notifyError(project, state.message)
                        SeerAnnotationService.getInstance(project).clearAll()
                    }
                    is SeerAnalysisState.Idle -> {
                        SeerAnnotationService.getInstance(project).clearAll()
                    }
                    else -> {}
                }
            }
        }
    }

    fun startAnalysis(baseBranch: String = "main") {
        currentJob?.cancel()
        analysisState.value = SeerAnalysisState.Submitting(0)

        if (useMockData) {
            startMockAnalysis()
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Seer Bug Prediction", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = "Gathering git info..."

                runBlocking {
                    val job = launch(Dispatchers.IO) {
                        runAnalysisCore(baseBranch, indicator)
                    }
                    currentJob = job
                    job.join()
                }
            }
        })
    }

    private suspend fun runAnalysisCore(baseBranch: String, indicator: ProgressIndicator?) {
        try {
            LOG.warn("Seer: starting analysis for base branch '$baseBranch'")
            val remoteInfo = GitUtils.getRemoteInfo(project)
            LOG.warn("Seer: remoteInfo = $remoteInfo")
            if (remoteInfo == null) {
                analysisState.value = SeerAnalysisState.Error("No git repository with an origin remote found")
                return
            }

            indicator?.fraction = 0.1
            indicator?.text = "Computing merge-base..."

            val mergeBase = GitUtils.getMergeBase(project, baseBranch)
            LOG.warn("Seer: mergeBase = $mergeBase")
            if (mergeBase == null) {
                analysisState.value = SeerAnalysisState.Error(
                    "Could not find merge-base between HEAD and '$baseBranch'. Is the branch correct?"
                )
                return
            }

            indicator?.fraction = 0.2
            indicator?.text = "Computing diff..."

            val diff = GitUtils.getDiff(project, mergeBase)
            LOG.warn("Seer: diff length = ${diff?.length ?: "null"}")
            if (diff.isNullOrBlank()) {
                analysisState.value = SeerAnalysisState.Error("No changes to analyze (empty diff)")
                return
            }

            val diffHash = diff.hashCode()
            if (diffHash == lastDiffHash && analysisState.value is SeerAnalysisState.Completed) {
                LOG.info("Diff unchanged since last analysis, skipping")
                return
            }

            val fileCount = diff.lines().count { it.startsWith("diff --git") }
            if (fileCount > MAX_DIFF_FILES) {
                analysisState.value = SeerAnalysisState.Error(
                    "Diff contains $fileCount files (max $MAX_DIFF_FILES). Please reduce the scope of changes."
                )
                return
            }
            if (diff.toByteArray().size > MAX_DIFF_SIZE_BYTES) {
                analysisState.value = SeerAnalysisState.Error(
                    "Diff is too large (max ${MAX_DIFF_SIZE_BYTES / 1024}KB). Please reduce the scope of changes."
                )
                return
            }

            analysisState.value = SeerAnalysisState.Submitting(fileCount)
            indicator?.fraction = 0.3
            indicator?.text = "Submitting $fileCount files to Seer..."

            val externalId = if (remoteInfo.provider == "github") {
                GitHubUtils.fetchGitHubRepoId(remoteInfo.owner, remoteInfo.name) ?: ""
            } else {
                ""
            }

            val commitMessages = GitUtils.getCommitMessages(project, mergeBase)

            val request = SeerAnalysisRequest(
                repo = SeerRepoInfo(
                    provider = remoteInfo.provider,
                    owner = remoteInfo.owner,
                    name = remoteInfo.name,
                    externalId = externalId,
                    baseCommitSha = mergeBase,
                    organizationId = 0
                ),
                diff = diff,
                commitMessage = commitMessages
            )

            val submitResponse = apiClient.submitAnalysis(request)
            LOG.warn("Seer: analysis submitted, run ID: ${submitResponse.runId}")

            analysisState.value = SeerAnalysisState.Polling(submitResponse.runId, submitResponse.status)
            indicator?.fraction = 0.4
            indicator?.text = "Analyzing code (run #${submitResponse.runId})..."

            val startTime = System.currentTimeMillis()
            var pollCount = 0
            while (coroutineContext.isActive) {
                if (System.currentTimeMillis() - startTime > POLL_TIMEOUT_MS) {
                    analysisState.value = SeerAnalysisState.Error("Analysis timed out after 10 minutes")
                    return
                }

                if (indicator?.isCanceled == true) {
                    analysisState.value = SeerAnalysisState.Idle
                    return
                }

                delay(POLL_INTERVAL_MS)
                pollCount++

                val pollFraction = 0.4 + (0.5 * (pollCount.coerceAtMost(100) / 100.0))
                indicator?.fraction = pollFraction

                val pollResponse = apiClient.pollResults(submitResponse.runId)
                indicator?.text = "Analyzing code (${pollResponse.status})..."
                analysisState.value = SeerAnalysisState.Polling(pollResponse.runId, pollResponse.status)

                when (pollResponse.status) {
                    "completed" -> {
                        lastDiffHash = diffHash
                        indicator?.fraction = 1.0
                        indicator?.text = "Analysis complete"
                        analysisState.value = SeerAnalysisState.Completed(
                            predictions = pollResponse.predictions ?: emptyList(),
                            diagnostics = pollResponse.diagnostics,
                            isDraft = false
                        )
                        LOG.warn("Seer: analysis completed: ${pollResponse.predictions?.size ?: 0} predictions")
                        return
                    }

                    "in_progress" -> {
                        if (pollResponse.isDraft && !pollResponse.predictions.isNullOrEmpty()) {
                            analysisState.value = SeerAnalysisState.Completed(
                                predictions = pollResponse.predictions,
                                diagnostics = pollResponse.diagnostics,
                                isDraft = true
                            )
                            LOG.warn("Seer: draft predictions received: ${pollResponse.predictions.size} predictions")
                        }
                    }

                    "errored" -> {
                        analysisState.value = SeerAnalysisState.Error(
                            pollResponse.errorMessage ?: "Analysis failed"
                        )
                        return
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.error("Analysis failed", e)
            indicator?.fraction = 1.0
            indicator?.text = "Analysis failed"
            analysisState.value = SeerAnalysisState.Error(e.message ?: "Unknown error")
        }
    }

    private fun startMockAnalysis() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Seer Bug Prediction", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = "Gathering git info..."

                runBlocking {
                    val job = launch(Dispatchers.IO) {
                        delay(500)
                        indicator.fraction = 0.2
                        indicator.text = "Computing diff..."
                        delay(500)
                        indicator.fraction = 0.4
                        indicator.text = "Submitting 3 files to Seer..."
                        analysisState.value = SeerAnalysisState.Submitting(3)
                        delay(500)
                        indicator.fraction = 0.6
                        indicator.text = "Analyzing code (processing)..."
                        analysisState.value = SeerAnalysisState.Polling(42, "processing")
                        delay(1000)
                        indicator.fraction = 1.0
                        indicator.text = "Analysis complete"

                        val mockPredictions = listOf(
                            SeerPrediction(
                                title = "Memory Leak from Unrecycled Screenshot Bitmap",
                                description = "A screenshot `Bitmap` is attached to the event hint via a lazy `byteProvider`. This provider is also responsible for recycling the bitmap's native memory via `bitmap.recycle()`. However, the provider is only invoked when the event is serialized into an envelope for sending. If the event is dropped for any reason, such as by an event processor, a `beforeSend` callback, sampling, or size limiting, the `byteProvider` is never called. Consequently, `bitmap.recycle()` is not executed, leading to a native memory leak. The accumulation of these unrecycled bitmaps can cause `OutOfMemoryError` and application crashes.",
                                shortDescription = "The screenshot Bitmap is not recycled if the event is dropped before being sent, causing a native memory leak that can lead to app crashes.",
                                suggestedFix = "Ensure the `Bitmap` is always recycled, regardless of whether the event is sent. This can be achieved by adding a `Closeable` to the `Hint` which handles the bitmap recycling. This ensures that when the SDK is done with the hint, the cleanup logic is triggered, preventing the memory leak even if the event is discarded.",
                                encodedLocation = "sentry-android-core/src/main/java/io/sentry/android/core/ScreenshotEventProcessor.java:136~142",
                                severity = "high"
                            ),
                            SeerPrediction(
                                title = "Potential Null Pointer in Activity Reference",
                                description = "The `activity` field is stored as a `WeakReference` which can be garbage collected at any point. The code dereferences `activity.get()` without a null check in the screenshot capture path, which could lead to a `NullPointerException` if the activity has been collected between the null check and usage.",
                                shortDescription = "WeakReference to Activity may return null between check and use, causing a NullPointerException during screenshot capture.",
                                suggestedFix = "Store the result of `activity.get()` in a local variable and use that for both the null check and subsequent operations to avoid a race condition with garbage collection.",
                                encodedLocation = "sentry-android-core/src/main/java/io/sentry/android/core/ScreenshotEventProcessor.java:95~102",
                                severity = "medium"
                            ),
                            SeerPrediction(
                                title = "Thread Safety Issue in Bitmap Compression",
                                description = "The `compress()` method is called on a Bitmap that could potentially be recycled from another thread. If the bitmap is recycled between the `isRecycled()` check and the `compress()` call, an `IllegalStateException` will be thrown.",
                                shortDescription = "Bitmap could be recycled by another thread between the recycled check and compress call.",
                                suggestedFix = "Synchronize access to the bitmap or perform the compression within a try-catch block to handle the case where the bitmap is recycled concurrently.",
                                encodedLocation = "sentry-android-core/src/main/java/io/sentry/android/core/ScreenshotEventProcessor.java:150~158",
                                severity = "low"
                            )
                        )

                        analysisState.value = SeerAnalysisState.Completed(
                            predictions = mockPredictions,
                            diagnostics = SeerDiagnostics(filesAnalyzed = 3, executionTimeSeconds = 2.5)
                        )
                        LOG.warn("Seer: mock analysis completed with ${mockPredictions.size} predictions")
                    }
                    currentJob = job
                    job.join()
                }
            }
        })
    }

    fun cancelAnalysis() {
        currentJob?.cancel()
        analysisState.value = SeerAnalysisState.Idle
    }

    fun isIdle(): Boolean {
        val state = analysisState.value
        return state is SeerAnalysisState.Idle ||
                state is SeerAnalysisState.Completed ||
                state is SeerAnalysisState.Error
    }

    fun hasFreshResults(): Boolean {
        val state = analysisState.value
        return lastDiffHash != null &&
                state is SeerAnalysisState.Completed &&
                !state.isDraft
    }

    fun clearLastDiffHash() {
        lastDiffHash = null
    }

    fun dispose() {
        scope.cancel()
        apiClient.close()
    }
}
