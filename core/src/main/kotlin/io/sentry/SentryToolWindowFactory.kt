package io.sentry

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import io.sentry.repository.SentryRepository
import io.sentry.ui.issues.Issues
import io.sentry.ui.issues.IssuesViewModel
import io.sentry.ui.livedebug.LiveDebug
import io.sentry.ui.livedebug.LiveDebugViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.ui.component.Text

@Suppress("unused")
@ExperimentalCoroutinesApi
internal class SentryToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        val repo = SentryRepository(cacheOnly = false)
        val issuesViewModel = IssuesViewModel(project, repo)
        val notificationManager = NotificationManager(project)
//        val profilingViewModel = ProfilingViewModel(project, repo)

        val liveDebugViewModel = LiveDebugViewModel(project, repo, notificationManager) {
            // todo find a better way
            toolWindow.contentManager.setSelectedContent(toolWindow.contentManager.contents[2])
            toolWindow.activate { }
        }

        @Suppress("DialogTitleCapitalization")
        toolWindow.stripeTitle = Bundle.message("toolWindowTitle")

        toolWindow.addComposeTab("Issues") {
            Issues(issuesViewModel, project)
        }
        toolWindow.addComposeTab("Releases") @Composable {
            Text(modifier = Modifier.Companion.padding(20.dp), text = "// TODO implement")
        }
        toolWindow.addComposeTab("🔥Live Debug 🔥") {
            LiveDebug(liveDebugViewModel)
        }
//        toolWindow.addComposeTab("Profiling") @Composable {
//            Profiling(profilingViewModel)
//        }

        toolWindow.disposable.whenDisposed {
            issuesViewModel.dispose()
            liveDebugViewModel.dispose()
        }
    }


}