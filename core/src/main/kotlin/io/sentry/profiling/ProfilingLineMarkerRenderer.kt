package io.sentry.profiling

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import io.sentry.ui.Colors
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

class ProfilingLineMarkerRenderer(
    private val sampleCount: Long,
    private val maxSampleCount: Long
) : LineMarkerRenderer {

    val good = Color(Colors.STATUS_OK.red, Colors.STATUS_OK.green, Colors.STATUS_OK.blue)
    val medium = Color(Colors.PERF.red, Colors.PERF.green, Colors.PERF.blue)
    val bad = Color(Colors.STATUS_ERROR.red, Colors.STATUS_ERROR.green, Colors.STATUS_ERROR.blue)

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
        if (maxSampleCount == 0L) return

        val intensity = (sampleCount.toDouble() / maxSampleCount.toDouble()).coerceIn(0.0, 1.0)
        val color = getColorForIntensity(intensity)

        g.color = color

        // Draw a narrow rectangle instead of filling the entire gutter
        val rectWidth = 6
        val rectX = r.x
        g.fillRect(rectX, r.y, rectWidth, r.height)
    }

    private fun getColorForIntensity(intensity: Double): Color {
        return when {
            intensity <= 0.5 -> {
                val factor = (intensity * 2).toFloat()
                interpolateColor(good, medium, factor)
            }

            else -> {
                val factor = ((intensity - 0.5) * 2).toFloat()
                interpolateColor(medium, bad, factor)
            }
        }
    }

    private fun interpolateColor(color1: Color, color2: Color, factor: Float): Color {
        val clampedFactor = factor.coerceIn(0f, 1f)
        val red = (color1.red + (color2.red - color1.red) * clampedFactor).toInt()
        val green = (color1.green + (color2.green - color1.green) * clampedFactor).toInt()
        val blue = (color1.blue + (color2.blue - color1.blue) * clampedFactor).toInt()
        val alpha = (color1.alpha + (color2.alpha - color1.alpha) * clampedFactor).toInt()
        return Color(red, green, blue, alpha)
    }
}