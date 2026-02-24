package io.sentry.seer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import io.sentry.seer.models.ParsedLocation
import io.sentry.seer.models.SeerPrediction
import io.sentry.seer.models.parseLocation
import io.sentry.ui.components.openFileInEditor
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

private val LOG = Logger.getInstance("io.sentry.seer.editor.SeerEditorPopup")

private const val POPUP_WIDTH = 520
private const val MAX_HEIGHT = 500
private const val PAD = 14
private val TEXT_WIDTH = POPUP_WIDTH - PAD * 2 // available width for text content
private val DETAIL_TEXT_WIDTH = TEXT_WIDTH - 20  // indented detail text width

class SeerGutterIconRenderer(
    private val prediction: SeerPrediction,
    private val project: Project
) : GutterIconRenderer() {

    override fun getIcon(): Icon = SeerSeverityIcon(prediction.severity)
    override fun getTooltipText(): String = "${prediction.title} (${prediction.severity})"

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            try {
                val editor = e.getData(CommonDataKeys.EDITOR)
                val location = prediction.parseLocation()
                val point = if (editor != null && location != null) {
                    anchorBelowAnnotation(editor, location.endLine)
                } else {
                    null
                }
                showPredictionPopup(prediction, project, point)
            } catch (ex: Exception) {
                LOG.error("Seer: failed to show popup", ex)
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is SeerGutterIconRenderer && prediction == other.prediction

    override fun hashCode(): Int = prediction.hashCode()
}

fun showPredictionPopup(prediction: SeerPrediction, project: Project, point: RelativePoint? = null) {
    var popupRef: JBPopup? = null
    var bodyRef: JPanel? = null
    var scrollRef: JScrollPane? = null

    val onToggle: () -> Unit = {
        SwingUtilities.invokeLater {
            popupRef?.let { p ->
                val root = p.content
                // Invalidate everything inside-out
                bodyRef?.invalidate()
                scrollRef?.viewport?.invalidate()
                scrollRef?.invalidate()
                root.invalidate()

                // Pack the underlying window — this forces a full resize (both grow and shrink)
                val window = SwingUtilities.getWindowAncestor(root)
                if (window != null) {
                    val pref = root.preferredSize
                    val newH = pref.height.coerceAtMost(MAX_HEIGHT)
                    window.size = Dimension(POPUP_WIDTH, newH)
                    window.validate()
                    window.repaint()
                } else {
                    root.validate()
                    root.repaint()
                    p.size = Dimension(root.preferredSize.width, root.preferredSize.height.coerceAtMost(MAX_HEIGHT))
                }
            }
        }
    }

    val onClose: () -> Unit = { popupRef?.cancel() }

    val (content, body, scroll) = buildContent(prediction, project, onToggle, onClose)
    bodyRef = body
    scrollRef = scroll

    val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(content, null)
        .setMovable(true)
        .setResizable(true)
        .setRequestFocus(true)
        .createPopup()

    popupRef = popup
    if (point != null) {
        popup.show(point)
    } else {
        popup.showInFocusCenter()
    }
}

// ─── Main content ───────────────────────────────────────────────────────────

private fun buildContent(prediction: SeerPrediction, project: Project, onToggle: () -> Unit, onClose: () -> Unit): Triple<JComponent, JPanel, JScrollPane> {
    val body = JPanel()
    body.layout = BoxLayout(body, BoxLayout.Y_AXIS)
    body.border = BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)

    // Header: severity badge
    val badgeRow = Box.createHorizontalBox().apply {
        alignmentX = Component.LEFT_ALIGNMENT
        add(SeverityBadge(prediction.severity))
        add(Box.createHorizontalGlue())
    }
    body.add(badgeRow)
    body.add(Box.createVerticalStrut(6))

    // Title (wrapping)
    val titleFg = UIUtil.getLabelForeground()
    val titleHex = String.format("#%02x%02x%02x", titleFg.red, titleFg.green, titleFg.blue)
    val titleFont = UIUtil.getLabelFont()
    val titleSize = titleFont.size + 1
    val titleHtml = prediction.title
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    val titlePane = JEditorPane().apply {
        val kit = HTMLEditorKit()
        val ss = StyleSheet()
        ss.addRule("body { color: $titleHex; font-family: ${titleFont.family}; font-size: ${titleSize}pt; font-weight: bold; margin: 0; padding: 0; }")
        kit.styleSheet = ss
        editorKit = kit
        contentType = "text/html"
        text = "<html><body>$titleHtml</body></html>"
        isEditable = false
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        setSize(TEXT_WIDTH, Short.MAX_VALUE.toInt())
    }
    val titleH = titlePane.preferredSize.height
    val titleWrapper = object : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(titlePane, BorderLayout.CENTER)
        }
        override fun getPreferredSize(): Dimension = Dimension(TEXT_WIDTH, titleH)
        override fun getMaximumSize(): Dimension = Dimension(TEXT_WIDTH, titleH)
        override fun getMinimumSize(): Dimension = Dimension(TEXT_WIDTH, titleH)
    }
    body.add(titleWrapper)
    body.add(Box.createVerticalStrut(10))

    // Short description
    body.add(htmlPane(prediction.shortDescription, TEXT_WIDTH))
    body.add(Box.createVerticalStrut(10))
    body.add(sep())
    body.add(Box.createVerticalStrut(6))

    // Collapsible: Details
    body.add(collapsibleSection("Details", prediction.description, onToggle))
    body.add(Box.createVerticalStrut(4))

    // Collapsible: Suggested Fix
    if (prediction.suggestedFix.isNotBlank()) {
        body.add(collapsibleSection("Suggested Fix", prediction.suggestedFix, onToggle))
        body.add(Box.createVerticalStrut(4))
    }

    // Collapsible: AI Fix Prompt
    val location = prediction.parseLocation()
    val aiPrompt = buildAgentPrompt(prediction, location)
    body.add(collapsibleSection("AI Fix Prompt", aiPrompt, onToggle))
    body.add(Box.createVerticalStrut(4))

    val scroll = JScrollPane(body).apply {
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        verticalScrollBar.unitIncrement = 16
        verticalScrollBar.blockIncrement = 80
    }

    // Sticky footer
    val footerContent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
    }

    if (location != null) {
        val fileName = location.filePath.substringAfterLast('/')
        val lineInfo = if (location.startLine == location.endLine) "line ${location.startLine}"
        else "lines ${location.startLine}-${location.endLine}"

        footerContent.add(JLabel("$fileName:${location.startLine}  ($lineInfo)").apply {
            alignmentX = Component.LEFT_ALIGNMENT
            foreground = JBColor(Color(60, 116, 221), Color(88, 166, 255))
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            icon = AllIcons.General.Locate
            iconTextGap = 4
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    openFileInEditor(project, location.filePath, location.startLine)
                }
            })
        })
        footerContent.add(Box.createVerticalStrut(4))
    }

    // "Fix with Claude Code" button
    footerContent.add(JLabel("Fix with Claude Code").apply {
        alignmentX = Component.LEFT_ALIGNMENT
        foreground = JBColor(Color(60, 116, 221), Color(88, 166, 255))
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        icon = AllIcons.Actions.Lightning
        iconTextGap = 4
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(aiPrompt), null)

                // Close the popup first
                onClose()

                // Try to open/focus Claude Code in the terminal
                val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                val action = actionManager.getAction("com.anthropic.code.plugin.actions.OpenClaudeInTerminalAction")
                if (action != null) {
                    val dataContext = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                        .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                        .build()
                    val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                        action, null, com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN, dataContext
                    )
                    action.actionPerformed(event)

                    // Write prompt directly to the Claude Code terminal
                    Timer(1000) {
                        SwingUtilities.invokeLater {
                            try {
                                writeToClaudeTerminal(project, aiPrompt)
                            } catch (ex: Exception) {
                                LOG.warn("Seer: could not auto-type into terminal, user can paste manually", ex)
                            }
                        }
                    }.apply { isRepeats = false; start() }
                }
            }
        })
    })

    val footer = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(0, PAD, PAD, PAD)
        isOpaque = false
        add(sep(), BorderLayout.NORTH)
        add(footerContent, BorderLayout.CENTER)
    }

    val footerH = footer.preferredSize.height
    val root = object : JPanel(BorderLayout()) {
        init {
            add(scroll, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }
        override fun getPreferredSize(): Dimension {
            // Recalculate body height each time (sections may have expanded)
            val bodyH = body.preferredSize.height
            return Dimension(POPUP_WIDTH, (bodyH + footerH).coerceIn(120, MAX_HEIGHT))
        }
    }
    return Triple(root, body, scroll)
}

// ─── Collapsible ────────────────────────────────────────────────────────────

private fun collapsibleSection(title: String, text: String, onToggle: () -> Unit): JPanel {
    val section = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    // Detail panel with subtle background
    val detailBg = JBColor(Color(0, 0, 0, 15), Color(255, 255, 255, 15))
    val detail = object : JPanel(BorderLayout()) {
        override fun paintComponent(g: Graphics) {
            g.color = detailBg
            g.fillRect(0, 0, width, height)
        }
    }.apply {
        border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
        isOpaque = false
        isVisible = false
    }
    detail.add(htmlPane(text, DETAIL_TEXT_WIDTH), BorderLayout.CENTER)

    val detailWrapper = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
        isVisible = false
        add(detail, BorderLayout.CENTER)
    }

    val colIcon = AllIcons.General.ArrowRight
    val expIcon = AllIcons.General.ArrowDown

    val toggleLabel = JLabel(title, colIcon, SwingConstants.LEFT).apply {
        font = font.deriveFont(Font.BOLD)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        iconTextGap = 4
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                val expanding = !detailWrapper.isVisible
                detailWrapper.isVisible = expanding
                detail.isVisible = expanding
                icon = if (expanding) expIcon else colIcon
                onToggle()
            }
        })
    }

    val copyBtn = JLabel(AllIcons.Actions.Copy).apply {
        toolTipText = "Copy to clipboard"
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(text), null)
                // Brief visual feedback
                icon = AllIcons.Actions.Checked
                Timer(1500) { icon = AllIcons.Actions.Copy }.apply {
                    isRepeats = false
                    start()
                }
            }
        })
    }

    val headerRow = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(toggleLabel, BorderLayout.CENTER)
        add(copyBtn, BorderLayout.EAST)
    }

    section.add(headerRow, BorderLayout.NORTH)
    section.add(detailWrapper, BorderLayout.CENTER)
    return section
}

// ─── HTML text pane ─────────────────────────────────────────────────────────

private fun htmlPane(text: String, widthPx: Int): JComponent {
    val escaped = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br>")
        .replace(Regex("`([^`]+)`"), "<code>$1</code>")

    val fg = UIUtil.getLabelForeground()
    val hex = String.format("#%02x%02x%02x", fg.red, fg.green, fg.blue)

    val kit = HTMLEditorKit()
    val ss = StyleSheet()
    ss.addRule("body { color: $hex; font-family: ${UIUtil.getLabelFont().family}; font-size: ${UIUtil.getLabelFont().size}pt; margin: 0; padding: 0; }")
    ss.addRule("code { background: #3c3c3c; padding: 1px 3px; }")
    kit.styleSheet = ss

    val pane = JEditorPane().apply {
        editorKit = kit
        contentType = "text/html"
        this.text = "<html><body>$escaped</body></html>"
        isEditable = false
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        // Pre-set width so the HTML view can calculate wrapped height
        setSize(widthPx, Short.MAX_VALUE.toInt())
    }

    // Now that the view knows the width, get the real preferred height
    val prefH = pane.preferredSize.height

    // Return a wrapper with fixed dimensions so BoxLayout respects it
    return object : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(pane, BorderLayout.CENTER)
        }
        override fun getPreferredSize(): Dimension = Dimension(widthPx, prefH)
        override fun getMaximumSize(): Dimension = Dimension(widthPx, prefH)
        override fun getMinimumSize(): Dimension = Dimension(widthPx, prefH)
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

private fun anchorBelowAnnotation(editor: Editor, endLine: Int): RelativePoint {
    val line0 = (endLine - 1).coerceIn(0, editor.document.lineCount - 1)
    // Get the Y position of the line *after* the last annotated line
    val nextLineVisual = editor.logicalToVisualPosition(
        com.intellij.openapi.editor.LogicalPosition(line0 + 1, 0)
    )
    val pointInEditor = editor.visualPositionToXY(nextLineVisual)
    // Convert from editor content coordinates to screen, then back to a RelativePoint
    val contentComponent = editor.contentComponent
    val screenPoint = Point(pointInEditor.x, pointInEditor.y)
    SwingUtilities.convertPointToScreen(screenPoint, contentComponent)
    // Use the gutter's X position so the popup aligns with the left edge
    val gutter = (editor as? com.intellij.openapi.editor.ex.EditorEx)?.gutterComponentEx
    if (gutter != null) {
        val gutterScreen = gutter.locationOnScreen
        screenPoint.x = gutterScreen.x
    }
    return RelativePoint(screenPoint)
}

private fun sep(): JComponent = JSeparator(SwingConstants.HORIZONTAL).apply {
    maximumSize = Dimension(Int.MAX_VALUE, 1)
    alignmentX = Component.LEFT_ALIGNMENT
}

// ─── Custom painting ────────────────────────────────────────────────────────

private class SeverityBadge(private val severity: String) : JComponent() {
    init {
        val w = severity.uppercase().length * 7 + 16
        preferredSize = Dimension(w, 20)
        minimumSize = preferredSize
        maximumSize = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.color = when (severity.lowercase()) {
            "critical" -> JBColor(Color(180, 35, 40), Color(245, 84, 89))
            "high" -> JBColor(Color(200, 60, 50), Color(235, 100, 80))
            "medium" -> JBColor(Color(180, 145, 0), Color(235, 192, 0))
            else -> JBColor(Color(80, 70, 160), Color(108, 95, 199))
        }
        g2.fill(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 6.0, 6.0))
        g2.color = Color.WHITE
        g2.font = font.deriveFont(Font.BOLD, 10f)
        val t = severity.uppercase()
        val fm = g2.fontMetrics
        g2.drawString(t, (width - fm.stringWidth(t)) / 2, (height + fm.ascent - fm.descent) / 2)
        g2.dispose()
    }
}

// ─── AI Agent Prompt ─────────────────────────────────────────────────────────

private fun buildAgentPrompt(prediction: SeerPrediction, location: ParsedLocation?): String {
    val locationStr = if (location != null) {
        val lineRange = if (location.startLine == location.endLine) "line ${location.startLine}"
        else "lines ${location.startLine}-${location.endLine}"
        "File: ${location.filePath} ($lineRange)"
    } else {
        "Location: ${prediction.encodedLocation}"
    }

    return """
        |## Bug Prediction: ${prediction.title}
        |
        |**Severity:** ${prediction.severity}
        |
        |**$locationStr**
        |
        |### Description
        |${prediction.description}
        |
        |### Suggested Fix
        |${prediction.suggestedFix}
        |
        |---
        |Please fix this issue. Apply the suggested fix at the specified location, ensuring the fix is correct and does not introduce regressions.
    """.trimMargin()
}

// ─── Terminal Integration ────────────────────────────────────────────────────

private fun writeToClaudeTerminal(project: Project, text: String) {
    val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
        .getToolWindow("Terminal") ?: run {
        LOG.warn("Seer: Terminal tool window not found")
        return
    }

    val claudeTab = toolWindow.contentManager.contents.find {
        it.tabName == "Claude Code"
    } ?: run {
        LOG.warn("Seer: Claude Code tab not found in terminal")
        return
    }

    // Walk the component tree to find the JBTerminalWidget's terminal panel
    // and get the TtyConnector to write directly
    val component = claudeTab.component
    val terminalWidget = findTerminalWidget(component)
    if (terminalWidget != null) {
        try {
            // Use reflection to access getTtyConnector()
            val getTtyMethod = terminalWidget.javaClass.getMethod("getTtyConnector")
            val ttyConnector = getTtyMethod.invoke(terminalWidget)
            if (ttyConnector != null) {
                val writeMethod = ttyConnector.javaClass.getMethod("write", ByteArray::class.java)
                // Send the prompt text
                writeMethod.invoke(ttyConnector, text.toByteArray(Charsets.UTF_8))
                // Send carriage return after a delay to submit (Enter = \r in terminals)
                Timer(500) {
                    try {
                        writeMethod.invoke(ttyConnector, "\r".toByteArray(Charsets.UTF_8))
                        LOG.warn("Seer: submitted prompt to Claude Code terminal")
                    } catch (ex: Exception) {
                        LOG.warn("Seer: could not auto-submit", ex)
                    }
                }.apply { isRepeats = false; start() }
                LOG.warn("Seer: wrote prompt to Claude Code terminal")
                return
            }
        } catch (ex: Exception) {
            LOG.warn("Seer: reflection approach failed, trying paste fallback", ex)
        }
    }

    // Fallback: simulate paste via the action system
    LOG.warn("Seer: could not find terminal widget, prompt is on clipboard")
}

private fun findTerminalWidget(component: java.awt.Component): java.awt.Component? {
    // Look for JBTerminalWidget by class name in the component hierarchy
    if (component.javaClass.name.contains("JBTerminalWidget") ||
        component.javaClass.name.contains("ShellTerminalWidget")) {
        return component
    }
    if (component is java.awt.Container) {
        for (child in component.components) {
            val found = findTerminalWidget(child)
            if (found != null) return found
        }
    }
    return null
}

private class SeerSeverityIcon(private val severity: String) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = when (severity.lowercase()) {
            "critical", "high" -> JBColor(Color(200, 60, 50), Color(245, 84, 89))
            "medium" -> JBColor(Color(180, 145, 0), Color(235, 192, 0))
            else -> JBColor(Color(80, 70, 160), Color(108, 95, 199))
        }
        g2.fillOval(x + 2, y + 2, 8, 8)
        g2.dispose()
    }
    override fun getIconWidth(): Int = 12
    override fun getIconHeight(): Int = 12
}
