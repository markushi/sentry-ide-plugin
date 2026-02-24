package io.sentry.seer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.sentry.seer.SeerAnalysisState
import io.sentry.seer.models.SeerPrediction
import io.sentry.seer.models.parseLocation
import io.sentry.ui.Colors
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun SeerPanel(viewModel: SeerViewModel) {
    val state by viewModel.state.collectAsState()
    val autoAnalyze by viewModel.autoAnalyze.collectAsState()
    var baseBranch by remember { mutableStateOf("main") }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CheckboxRow(
            checked = autoAnalyze,
            onCheckedChange = { viewModel.setAutoAnalyze(it) },
            text = "Auto-analyze on idle (5s)"
        )

        Spacer(modifier = Modifier.height(4.dp))

        when (val currentState = state) {
            is SeerAnalysisState.Idle -> {
                IdleView(
                    baseBranch = baseBranch,
                    onBaseBranchChanged = { baseBranch = it },
                    onRunClicked = { viewModel.runAnalysis(baseBranch) }
                )
            }

            is SeerAnalysisState.Submitting -> {
                ProgressView("Submitting analysis (${currentState.fileCount} files)...")
            }

            is SeerAnalysisState.Polling -> {
                PollingView(
                    runId = currentState.runId,
                    status = currentState.status,
                    onCancelClicked = { viewModel.cancel() }
                )
            }

            is SeerAnalysisState.Completed -> {
                CompletedView(
                    predictions = currentState.predictions,
                    diagnostics = currentState.diagnostics,
                    isDraft = currentState.isDraft,
                    onPredictionClicked = { viewModel.onPredictionClicked(it) },
                    onRerunClicked = { viewModel.runAnalysis(baseBranch) },
                    onCancelClicked = { viewModel.cancel() }
                )
            }

            is SeerAnalysisState.Error -> {
                ErrorView(
                    message = currentState.message,
                    onRetryClicked = { viewModel.runAnalysis(baseBranch) }
                )
            }
        }
    }
}

@Composable
private fun IdleView(
    baseBranch: String,
    onBaseBranchChanged: (String) -> Unit,
    onRunClicked: () -> Unit
) {
    Text(text = "Analyze your local changes for potential bugs using Seer.")
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Base branch:")
        TextField(
            value = baseBranch,
            onValueChange = onBaseBranchChanged,
            modifier = Modifier.width(150.dp)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    DefaultButton(onClick = onRunClicked) {
        Text("Run Seer Analysis")
    }
}

@Composable
private fun ProgressView(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = Colors.STATUS_PENDING, shape = CircleShape)
        )
        Text(text = message)
    }
}

@Composable
private fun PollingView(runId: Int, status: String, onCancelClicked: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = Colors.STATUS_PENDING, shape = CircleShape)
        )
        Text(text = "Analyzing... (run #$runId, status: $status)")
    }
    Spacer(modifier = Modifier.height(4.dp))
    OutlinedButton(onClick = onCancelClicked) {
        Text("Cancel")
    }
}

@Composable
private fun CompletedView(
    predictions: List<SeerPrediction>,
    diagnostics: io.sentry.seer.models.SeerDiagnostics?,
    isDraft: Boolean,
    onPredictionClicked: (SeerPrediction) -> Unit,
    onRerunClicked: () -> Unit,
    onCancelClicked: () -> Unit
) {
    if (predictions.isEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = Colors.STATUS_OK, shape = CircleShape)
            )
            Text(text = "No issues found")
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isDraft) Colors.STATUS_PENDING else Colors.STATUS_ERROR,
                        shape = CircleShape
                    )
            )
            Text(
                text = if (isDraft) "Draft results (analyzing...)"
                else "Found ${predictions.size} potential issue(s)"
            )
        }
    }

    if (!isDraft && diagnostics != null) {
        val info = buildString {
            diagnostics.filesAnalyzed?.let { append("$it files analyzed") }
            diagnostics.executionTimeSeconds?.let {
                if (isNotEmpty()) append(" | ")
                append("%.1fs".format(it))
            }
        }
        if (info.isNotEmpty()) {
            Text(text = info)
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    if (isDraft) {
        OutlinedButton(onClick = onCancelClicked) {
            Text("Cancel")
        }
    } else {
        OutlinedButton(onClick = onRerunClicked) {
            Text("Re-run Analysis")
        }
    }

    if (predictions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(predictions) { prediction ->
                PredictionRow(prediction, onPredictionClicked)
            }
        }
    }
}

@Composable
private fun PredictionRow(prediction: SeerPrediction, onClick: (SeerPrediction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(prediction) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Severity dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = severityColor(prediction.severity),
                    shape = CircleShape
                )
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = prediction.title)
            Text(text = prediction.shortDescription)

            val location = prediction.parseLocation()
            if (location != null) {
                Text(text = "${location.filePath}:${location.startLine}-${location.endLine}")
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetryClicked: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = Colors.STATUS_ERROR, shape = CircleShape)
        )
        Text(text = message)
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = onRetryClicked) {
        Text("Retry")
    }
}

private fun severityColor(severity: String): Color {
    return when (severity.lowercase()) {
        "critical", "high" -> Colors.STATUS_ERROR
        "medium" -> Colors.PERF
        else -> Color(108, 95, 199)
    }
}
