package io.sentry.ui.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project as IntellijProject

private val logger = Logger.getInstance("StacktraceUtils")

data class StacktraceLine(
    val text: String,
    val isClickable: Boolean,
    val fileName: String? = null,
    val lineNumber: Int? = null
)

/**
 * Parses stacktrace text into individual lines with clickability information
 */
fun parseStacktrace(stacktraceText: String): List<StacktraceLine> {
    return stacktraceText.lines().map { line ->
        val clickableInfo = extractFileInfo(line)
        StacktraceLine(
            text = line,
            isClickable = clickableInfo != null,
            fileName = clickableInfo?.first,
            lineNumber = clickableInfo?.second
        )
    }
}

/**
 * Extracts file name and line number from a stacktrace line
 * Matches patterns like:
 * - "at SomeClass.method (file.java:123)"
 * - "at package.Class.method(file.kt:45)"
 * - "  File "/path/to/file.py", line 123, in method"
 */
private fun extractFileInfo(line: String): Pair<String, Int>? {
    // Java/Kotlin pattern: (filename:line)
    val javaPattern = Regex("""\(([^)]+\.(?:java|kt|scala|groovy)):(\d+)\)""")
    val javaMatch = javaPattern.find(line)
    if (javaMatch != null) {
        val fileName = javaMatch.groupValues[1]
        val lineNumber = javaMatch.groupValues[2].toIntOrNull()
        if (lineNumber != null) {
            return fileName to lineNumber
        }
    }

    // Python pattern: File "filename", line number
    val pythonPattern = Regex("""File "([^"]+\.py)", line (\d+)""")
    val pythonMatch = pythonPattern.find(line)
    if (pythonMatch != null) {
        val fileName = pythonMatch.groupValues[1]
        val lineNumber = pythonMatch.groupValues[2].toIntOrNull()
        if (lineNumber != null) {
            return fileName to lineNumber
        }
    }

    // JavaScript pattern: at filename:line:column
    val jsPattern = Regex("""at .+\((.+\.(?:js|ts|jsx|tsx)):(\d+):\d+\)""")
    val jsMatch = jsPattern.find(line)
    if (jsMatch != null) {
        val fileName = jsMatch.groupValues[1]
        val lineNumber = jsMatch.groupValues[2].toIntOrNull()
        if (lineNumber != null) {
            return fileName to lineNumber
        }
    }

    return null
}

/**
 * Opens a file at a specific line number in the IntelliJ editor
 */
fun openFileInEditor(project: IntellijProject, fileName: String, lineNumber: Int?) {
    logger.info("Opening file: $fileName at line $lineNumber")
    ApplicationManager.getApplication().invokeLater {
        val virtualFile = findFileInProject(project, fileName)
        if (virtualFile != null) {
            logger.info("Found file using enhanced search: ${virtualFile.path}")
            val descriptor = OpenFileDescriptor(project, virtualFile, (lineNumber ?: 1) - 1, 0)
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        } else {
            logger.warn("Could not find file: $fileName")
        }
    }
}

/**
 * Finds a file in the project using PSI APIs
 */
private fun findFileInProject(project: IntellijProject, fileName: String): VirtualFile? {
    val justFileName = extractFileName(fileName)
    if (justFileName.isEmpty()) {
        logger.warn("Could not extract filename from: $fileName")
        return null
    }

    logger.info("Using PSI search for filename: $justFileName (from path: $fileName)")

    // Search for files by name in project scope using PSI
    val virtualFiles = FilenameIndex.getVirtualFilesByName(
        project,
        justFileName,
        GlobalSearchScope.projectScope(project)
    )

    logger.info("PSI search found ${virtualFiles.size} matching files")
    return if (virtualFiles.isNotEmpty()) {
        val bestMatch = findBestMatchingFile(virtualFiles.toList(), fileName)
        logger.info("Selected best match: ${bestMatch.path}")
        bestMatch
    } else {
        null
    }
}

/**
 * Extracts just the filename from a path (handles both forward and backslashes)
 */
private fun extractFileName(filePath: String): String {
    return filePath.substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
}

/**
 * Finds the best matching file from multiple candidates based on path similarity
 */
private fun findBestMatchingFile(candidates: List<VirtualFile>, targetPath: String): VirtualFile {
    if (candidates.size == 1) {
        return candidates[0]
    }

    // Score each candidate based on path similarity
    val scored = candidates.map { virtualFile ->
        val score = calculatePathSimilarity(virtualFile.path, targetPath)
        virtualFile to score
    }

    // Return the candidate with the highest score
    return scored.maxByOrNull { it.second }?.first ?: candidates[0]
}

/**
 * Simple path similarity scoring - counts matching path segments
 */
private fun calculatePathSimilarity(candidatePath: String, targetPath: String): Int {
    val candidateSegments = candidatePath.split('/', '\\').filter { it.isNotEmpty() }
    val targetSegments = targetPath.split('/', '\\').filter { it.isNotEmpty() }

    var score = 0
    val minSize = minOf(candidateSegments.size, targetSegments.size)

    // Score based on matching segments from the end (filename is most important)
    for (i in 1..minSize) {
        if (candidateSegments.getOrNull(candidateSegments.size - i) ==
            targetSegments.getOrNull(targetSegments.size - i)
        ) {
            score += i // Later segments get higher weight
        }
    }

    return score
}