package io.sentry.profiling

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class AnnotateWithSentryProfilingAction : AnAction("🔥Annotate with Sentry Profiling") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val annotationService = EditorAnnotationService.getInstance(project)

        val hasAnnotations = annotationService.hasAnnotations(editor)
        if (hasAnnotations) {
            annotationService.clearAnnotations(editor)
        } else {
            annotationService.applyProfilingAnnotations(editor, file)
        }
    }
}