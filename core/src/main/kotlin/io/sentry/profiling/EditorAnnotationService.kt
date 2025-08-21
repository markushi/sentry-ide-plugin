package io.sentry.profiling

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.sentry.logging.Logger
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class EditorAnnotationService(private val project: Project) {

    private val activeHighlighters = ConcurrentHashMap<Editor, List<RangeHighlighter>>()

    fun applyProfilingAnnotations(editor: Editor, file: VirtualFile) {
        clearAnnotations(editor)

        val document = editor.document
        val markupModel = editor.markupModel

        val profilingService = ProfilingDataService.getInstance(project)
        val profilingData = profilingService.getProfilingDataForFile(file)

        val highlighters = mutableListOf<RangeHighlighter>()

        val maxSampleCount = profilingData.first
        for ((lineNumber, sampleCount) in profilingData.second) {
            // Convert to 0-based line number
            val line = lineNumber - 1

            if (line >= 0 && line < document.lineCount) {
                val lineStartOffset = document.getLineStartOffset(line)
                val lineEndOffset = document.getLineEndOffset(line)
                val renderer = ProfilingLineMarkerRenderer(sampleCount, maxSampleCount)
                val highlighter = markupModel.addRangeHighlighter(
                    lineStartOffset,
                    lineEndOffset,
                    HighlighterLayer.ADDITIONAL_SYNTAX,
                    null, // No text attributes, only line marker
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                highlighter.lineMarkerRenderer = renderer
                highlighters.add(highlighter)
            }
        }

        activeHighlighters[editor] = highlighters
        Logger.debug("EditorAnnotationService", "Applied ${highlighters.size} profiling annotations to ${file.name}")
    }

    fun clearAnnotations(editor: Editor) {
        activeHighlighters[editor]?.forEach { highlighter ->
            editor.markupModel.removeHighlighter(highlighter)
        }
        activeHighlighters.remove(editor)
    }

    fun hasAnnotations(editor: Editor): Boolean {
        return activeHighlighters.containsKey(editor)
    }

    companion object {
        fun getInstance(project: Project): EditorAnnotationService {
            return project.getService(EditorAnnotationService::class.java)
        }
    }
}