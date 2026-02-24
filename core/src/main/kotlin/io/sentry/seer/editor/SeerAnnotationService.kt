package io.sentry.seer.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.diagnostic.Logger
import io.sentry.seer.models.ParsedLocation
import io.sentry.seer.models.SeerPrediction
import io.sentry.seer.models.parseLocation
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class SeerAnnotationService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(SeerAnnotationService::class.java)
        val PREDICTION_KEY = Key.create<SeerPrediction>("seer.prediction")

        fun getInstance(project: Project): SeerAnnotationService {
            return project.getService(SeerAnnotationService::class.java)
        }
    }

    private val activeHighlighters = ConcurrentHashMap<Editor, MutableList<RangeHighlighter>>()

    fun applyPredictions(predictions: List<SeerPrediction>) {
        // Phase 1: resolve files on a background thread with read access
        ApplicationManager.getApplication().executeOnPooledThread {
            val resolved = mutableListOf<Triple<SeerPrediction, ParsedLocation, VirtualFile>>()

            ReadAction.run<Throwable> {
                for (prediction in predictions) {
                    LOG.warn("Seer: processing prediction: encodedLocation='${prediction.encodedLocation}', title='${prediction.title}'")
                    val location = prediction.parseLocation()
                    if (location == null) {
                        LOG.warn("Seer: could not parse location from '${prediction.encodedLocation}'")
                        continue
                    }
                    val file = findFileInProject(location.filePath)
                    if (file == null) {
                        LOG.warn("Seer: could not find file '${location.filePath}' in project")
                        continue
                    }
                    LOG.warn("Seer: resolved '${location.filePath}' -> ${file.path}")
                    resolved.add(Triple(prediction, location, file))
                }
            }

            // Phase 2: apply highlighters on EDT
            ApplicationManager.getApplication().invokeLater {
                clearAll()

                for ((prediction, location, file) in resolved) {
                    val editors = FileEditorManager.getInstance(project).allEditors
                        .mapNotNull { it as? com.intellij.openapi.fileEditor.TextEditor }
                        .filter { it.file == file }
                        .map { it.editor }

                    for (editor in editors) {
                        applyToEditor(editor, prediction, location.startLine, location.endLine)
                    }
                }

                LOG.warn("Seer: applied annotations for ${resolved.size} predictions")
            }
        }
    }

    private fun applyToEditor(editor: Editor, prediction: SeerPrediction, startLine: Int, endLine: Int) {
        val document = editor.document
        val markupModel = editor.markupModel

        val line0 = (startLine - 1).coerceIn(0, document.lineCount - 1)
        val lineEnd0 = (endLine - 1).coerceIn(0, document.lineCount - 1)

        val startOffset = document.getLineStartOffset(line0)
        val endOffset = document.getLineEndOffset(lineEnd0)

        val renderer = SeerLineMarkerRenderer(prediction.severity)
        val highlighter = markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            null,
            HighlighterTargetArea.LINES_IN_RANGE
        )
        highlighter.lineMarkerRenderer = renderer
        highlighter.putUserData(PREDICTION_KEY, prediction)

        highlighter.gutterIconRenderer = SeerGutterIconRenderer(prediction, project)

        activeHighlighters.getOrPut(editor) { mutableListOf() }.add(highlighter)
        LOG.warn("Seer: applied annotation for '${prediction.title}' at lines $startLine-$endLine")
    }

    fun clearAll() {
        for ((editor, highlighters) in activeHighlighters) {
            for (highlighter in highlighters) {
                editor.markupModel.removeHighlighter(highlighter)
            }
        }
        activeHighlighters.clear()
    }

    private fun findFileInProject(filePath: String): VirtualFile? {
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
        if (fileName.isEmpty()) return null

        val files = FilenameIndex.getVirtualFilesByName(
            project,
            fileName,
            GlobalSearchScope.projectScope(project)
        )

        if (files.isEmpty()) return null
        if (files.size == 1) return files.first()

        // Score by path similarity
        return files.maxByOrNull { vf ->
            val candidateSegments = vf.path.split('/').filter { it.isNotEmpty() }
            val targetSegments = filePath.split('/', '\\').filter { it.isNotEmpty() }
            var score = 0
            val minSize = minOf(candidateSegments.size, targetSegments.size)
            for (i in 1..minSize) {
                if (candidateSegments.getOrNull(candidateSegments.size - i) ==
                    targetSegments.getOrNull(targetSegments.size - i)
                ) {
                    score += i
                }
            }
            score
        }
    }
}
