package io.sentry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import java.awt.Cursor

@Composable
fun ClickableStacktrace(
    stacktraceText: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Gray.copy(alpha = 0.1f),
    height: Dp = 200.dp,
    onStackTraceLineClicked: (line: StacktraceLine) -> Unit
) {
    val stacktraceLines = remember(stacktraceText) { parseStacktrace(stacktraceText) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(backgroundColor, shape = RoundedCornerShape(4.dp))
            .padding(8.dp)
            .verticalScroll(scrollState)
    ) {
        stacktraceLines.forEach { line ->
            ClickableStacktraceLine(
                stacktraceLine = line,
                modifier = Modifier.fillMaxWidth(),
                onStackTraceLineClicked
            )
        }
    }
}

@Composable
private fun ClickableStacktraceLine(
    stacktraceLine: StacktraceLine,
    modifier: Modifier = Modifier,
    onStackTraceLineClicked: (line: StacktraceLine) -> Unit
) {
    val style = JewelTheme.editorTextStyle.copy(
        color = Color(0xFF1376E6)
    )
    Text(
        text = stacktraceLine.text,
        style = style,
        modifier = modifier
            .clickable {
                onStackTraceLineClicked(stacktraceLine)
            }
            .pointerHoverIcon(
                if (stacktraceLine.isClickable) {
                    PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                } else {
                    PointerIcon.Default
                }
            ).padding(vertical = 1.dp),
        maxLines = 1
    )
}