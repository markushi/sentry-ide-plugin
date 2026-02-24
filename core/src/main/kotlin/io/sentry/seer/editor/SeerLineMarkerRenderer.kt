package io.sentry.seer.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import io.sentry.ui.Colors
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

class SeerLineMarkerRenderer(private val severity: String) : LineMarkerRenderer {

    private val critical = Color(Colors.STATUS_ERROR.red, Colors.STATUS_ERROR.green, Colors.STATUS_ERROR.blue)
    private val medium = Color(Colors.PERF.red, Colors.PERF.green, Colors.PERF.blue)
    private val low = Color(108, 95, 199) // Blue/info from theme

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
        g.color = colorForSeverity()
        val rectWidth = 6
        g.fillRect(r.x, r.y, rectWidth, r.height)
    }

    private fun colorForSeverity(): Color {
        return when (severity.lowercase()) {
            "critical", "high" -> critical
            "medium" -> medium
            else -> low
        }
    }
}
