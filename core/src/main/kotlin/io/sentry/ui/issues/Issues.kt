package io.sentry.ui.issues

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sentry.network.models.*
import io.sentry.ui.Colors
import io.sentry.ui.UiState
import io.sentry.ui.components.BasicTableLayout
import io.sentry.ui.components.ClickableStacktrace
import io.sentry.ui.components.StacktraceLine
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import java.awt.Desktop
import java.util.*
import com.intellij.openapi.project.Project as IntellijProject

@Composable
fun Issues(viewModel: IssuesViewModel, intellijProject: IntellijProject) {
    val uiState by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        when (uiState.selectedIssue) {
            null -> {
                ProjectSelector(uiState) { project ->
                    viewModel.onProjectSelected(project)
                }
                IssuesListView(uiState.issues) { issue ->
                    viewModel.onIssueSelected(issue)
                }
            }

            else -> {
                IssueDetailOverview(uiState, onBackClicked = {
                    viewModel.onIssueBackClicked()
                }, onStackTraceLineClicked = { line ->
                    viewModel.onStacktraceLineClicked(line)
                })
            }
        }
    }
}

@Composable
private fun ProjectSelector(uiState: IssuesUiState, onProjectSelected: (project: Project) -> Unit) {
    when (val projectsState = uiState.projects) {
        is UiState.Undefined -> {

        }

        is UiState.Loading -> {
            Text("io.sentry.ui.Loading projects...")
        }

        is UiState.Error -> {
            Text("Error loading projects: ${projectsState.exception.message}")
        }

        is UiState.Success -> {
            Dropdown(modifier = Modifier.defaultMinSize(minWidth = 120.dp), menuContent = {
                projectsState.data.forEach { project ->
                    selectableItem(uiState.selectedProject == project, onClick = {
                        onProjectSelected(project)
                    }, content = {
                        Text(text = project.asSlug())
                    })
                }
            }, content = {
                Text(
                    text = uiState.selectedProject?.asSlug() ?: "Choose a project...", maxLines = 2
                )
            })
        }
    }
}

@Composable
fun NoIssues(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Hooray, no issues.")
    }
}

@Composable
fun LoadingIssues(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading...")
    }
}


@Composable
private fun LoadingFailed(issuesState: UiState.Error) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Error loading issues: ${issuesState.exception.message}")
    }
}

@Composable
fun IssuesListView(issues: UiState<List<Issue>>, onIssueSelected: (issue: Issue) -> Unit) {
    when (issues) {
        is UiState.Undefined -> {

        }

        is UiState.Loading -> {
            LoadingIssues()
        }

        is UiState.Error -> {
            LoadingFailed(issues)
        }

        is UiState.Success -> {
            if (issues.data.isEmpty()) {
                NoIssues()
            } else {
                var maxCount = 1
                issues.data.forEach { issue ->
                    issue.stats?.last24Hours?.forEach { stat ->
                        if (stat[1] > maxCount) {
                            maxCount = stat[1]
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    val columnState = rememberLazyListState()
                    LazyColumn(state = columnState) {
                        items(issues.data) { issue ->
                            IssueRow(issue, maxCount) {
                                onIssueSelected(issue)
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                        scrollState = columnState,
                    )
                }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: Issue, maxCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.defaultMinSize(90.dp, 20.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var labelColor = JewelTheme.defaultTextStyle.background
        var label = "<unknown>"
        when {
            issue.issueType == "error" -> {
                label = "ERR"
                labelColor = Colors.STATUS_ERROR
            }

            issue.issueType?.startsWith("performance") ?: false -> {
                label = "PERF"
                labelColor = Colors.PERF
            }
        }
        Box(
            modifier = Modifier.size(40.dp, 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                color = labelColor,
                text = label,
                fontSize = JewelTheme.defaultTextStyle.fontSize * 0.8f
            )
        }
        StatsBar(modifier = Modifier.size(60.dp, 20.dp), issue, maxCount)
        Column(modifier = Modifier.fillMaxWidth(0.80f)) {
            Text(text = issue.title, maxLines = 1)
            Text(text = issue.culprit ?: "", maxLines = 1)
        }
        Text(text = "${issue.count?.toIntOrNull()?.formatCompact() ?: issue.count ?: ""} Events", maxLines = 1)
        Text(text = "${issue.userCount?.formatCompact() ?: "0"} Users", maxLines = 1)
    }
}

@Composable
private fun StatsBar(
    modifier: Modifier = Modifier, issue: Issue, maxCount: Int
) {
    val barColor = JewelTheme.defaultTextStyle.color

    issue.stats?.last24Hours?.let { stats ->
        val max = maxCount.toFloat() / 2f
        Canvas(
            modifier = modifier.defaultMinSize(48.dp, 12.dp).clipToBounds()
        ) {
            for (i in 0 until stats.size) {
                val strokeWidth = size.width * 0.5f / stats.size
                val stepX = size.width / (stats.size + 1f)
                val x = (i + 1) * stepX
                val y = size.height - stats[i][1].toFloat() / max * size.height

                drawLine(
                    color = barColor,
                    start = Offset(x, size.height),
                    end = Offset(x, y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun IssueDetailOverview(
    uiState: IssuesUiState,
    onBackClicked: () -> Unit,
    onStackTraceLineClicked: (line: StacktraceLine) -> Unit
) {
    val selectedIssue = uiState.selectedIssue ?: return
    Row(modifier = Modifier.fillMaxSize()) {
        IssueDetails(selectedIssue, uiState.issueEvents, onBackClicked, onStackTraceLineClicked)
        IssueTags(
            modifier = Modifier.padding(8.dp),
            tagOverview = uiState.tagOverview
        )
    }
}

@Composable
fun IssueDetails(
    selectedIssue: Issue,
    selectedIssueEvents: UiState<List<Event>>,
    onBackClicked: () -> Unit,
    onStackTraceLineClicked: (line: StacktraceLine) -> Unit
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth(0.7f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedButton(
                onClick = onBackClicked
            ) {
                Text("← Back to Issues")
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedIssue.title,
                    style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 1.2)
                )

                DefaultButton(onClick = {
                    selectedIssue.permalink?.let {
                        try {
                            Desktop.getDesktop().browse(java.net.URI(it));
                        } catch (_: Exception) {
                        }
                    }
                }) {
                    Text("→ view on sentry.io")
                }
            }
        }
        if (selectedIssue.culprit != null) {
            Text("Culprit: ${selectedIssue.culprit}")
            Spacer(modifier = Modifier.size(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoCard("Events", selectedIssue.count?.toIntOrNull()?.formatCompact() ?: "0")
            InfoCard("Users", selectedIssue.userCount?.formatCompact() ?: "0")
            InfoCard("Level", selectedIssue.level ?: "Unknown")
            InfoCard("Platform", selectedIssue.platform ?: "Unknown")
        }
        Spacer(modifier = Modifier.size(12.dp))

        val index = remember { mutableIntStateOf(0) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recent Events",
                style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 1.1)
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    enabled = index.value > 0,
                    onClick = {
                        index.value -= 1
                    }) {
                    Text("<")
                }
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(
                    enabled = selectedIssueEvents is UiState.Success && index.value < selectedIssueEvents.data.size - 1,
                    onClick = {
                        index.value += 1
                    }) {
                    Text(">")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxSize()) {
            when (selectedIssueEvents) {
                is UiState.Undefined -> {

                }

                is UiState.Loading -> {
                    LoadingIssues()
                }

                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error loading events: ${selectedIssueEvents.exception.message}")
                    }
                }

                is UiState.Success -> {
                    if (selectedIssueEvents.data.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No events found")
                        }
                    } else {
                        EventRow(selectedIssueEvents.data[index.value], onStackTraceLineClicked)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    Column(
        modifier = Modifier.background(Color.Gray.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)).padding(12.dp)
    ) {
        Text(
            text = label,
            style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 1.1)
        )
    }
}

@Composable
private fun EventRow(event: Event, onStackTraceLineClicked: (line: StacktraceLine) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Gray.copy(alpha = 0.05f), shape = RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = event.title, maxLines = 1)
            Text(
                text = event.dateCreated?.take(19)?.replace("T", " ") ?: "-",
                style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9)
            )
        }

        if (event.location != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.location,
                style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9),
                maxLines = 1
            )
        }

        if (event.message != null && event.message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.message,
                style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9),
                maxLines = 2
            )
        }

        // Display full stacktrace in a text area if available
        val stacktraceText = formatStacktrace(event)
        if (stacktraceText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Stacktrace:",
                style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 0.9)
            )
            Spacer(modifier = Modifier.height(4.dp))

            ClickableStacktrace(
                stacktraceText = stacktraceText,
                modifier = Modifier.fillMaxWidth(),
                height = 200.dp,
                onStackTraceLineClicked = onStackTraceLineClicked
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun formatStacktrace(event: Event): String {
    return buildString {
        event.entries.forEach { entry ->
            if (entry is EventEntry.ExceptionEventEntry) {
                if (entry.data?.values?.isNotEmpty() == true) {
                    entry.data.values.forEach { exception ->
                        // Add exception header
                        appendLine("${exception.type}: ${exception.value}")

                        // Add stack frames
                        exception.stacktrace?.frames?.forEach { frame ->
                            append("\tat ")

                            // Add module and function
                            if (frame.module != null) {
                                append(frame.module)
                                if (frame.function != null) {
                                    append(".${frame.function}")
                                }
                            } else if (frame.function != null) {
                                append(frame.function)
                            }

                            // Add file location
                            if (frame.filename != null) {
                                append(" (${frame.filename}")
                                if (frame.lineNo != null) {
                                    append(":${frame.lineNo}")
                                }
                                append(")")
                            }

                            appendLine()
                        }

                        // Add separator between exceptions
                        if (entry.data.values.size > 1) {
                            appendLine()
                            appendLine("Caused by:")
                        }
                    }
                }
            }
        }
    }.trim()
}

@Composable
fun IssueTags(
    modifier: Modifier,
    tagOverview: UiState<List<TagOverview>>
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(state = scrollState),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (tagOverview) {
            is UiState.Loading -> Text(text = "loading...")
            is UiState.Error -> Text(text = "Failed to load: ${tagOverview.exception.message}")
            is UiState.Success -> TagOverview(tagOverview.data)
            is UiState.Undefined -> {}
        }
    }
}

@Composable
private fun TagOverview(tagOverviews: List<TagOverview>) {
    val header = listOf<@Composable (() -> Unit)>({
        Text(
            modifier = Modifier.width(120.dp),
            text = "Tags",
            maxLines = 1,
            style = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize * 1.1),
            overflow = TextOverflow.Ellipsis
        )
    }, { Text(text = "") }, { Text(text = "") }, { Text(text = "") })

    val rows = listOf(header) + tagOverviews.map { tag ->
        listOf<@Composable (() -> Unit)>({
            Text(
                modifier = Modifier.width(120.dp),
                text = tag.name.lowercase(Locale.US),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }, {
            Canvas(
                modifier = Modifier.padding(4.dp).size(90.dp, 24.dp)
            ) {
                val outline = 6.dp.toPx()
                val cornerRadius = 4.dp.toPx()
                val spaceBetween = 2.dp.toPx()

                // background
                //drawRoundRect(
                //    color = bgColor,
                //    topLeft = Offset(0f, 0f),
                //    size = Size(size.width, size.height),
                //    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                //)

                val availableWidth =
                    size.width - outline - (if (tag.topValues.size > 1) ((tag.topValues.size - 1) * spaceBetween) else 0f)
                var currentX = outline / 2f

                for (index in 0 until tag.topValues.size) {
                    val item = tag.topValues[index]
                    val mutedColor = Colors.getColorMuted(index)
                    val color = Colors.getColor(index)
                    val percentage = (item.count.toDouble() / tag.totalValues.toDouble()).toFloat()
                    val endX = currentX + percentage * availableWidth
                    drawRoundRect(
                        brush = Brush.linearGradient(listOf(mutedColor, color)),
                        topLeft = Offset(currentX, outline / 2f),
                        size = Size(endX - currentX, size.height - outline),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                    )
                    currentX = endX + spaceBetween
                }
            }
        }, {
            val firstValue = tag.topValues.firstOrNull()
            if (firstValue != null) {
                val percentage = (firstValue.count.toDouble() / tag.totalValues.toDouble()).toFloat()
                val percentageString = String.format("%.2f", percentage * 100.0) + "%"
                Text(text = percentageString)
            } else {
                Text("-")
            }

        }, {
            val firstValue = tag.topValues.firstOrNull()
            Text(
                modifier = Modifier.width(80.dp),
                text = firstValue?.name ?: "-",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        })
    }
    BasicTableLayout(
        rows.size, 4, Color.Transparent, cellBorderWidth = 4.dp, rows = rows
    )
}
