package io.sentry.seer.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

data class RemoteInfo(val provider: String, val owner: String, val name: String)

object GitUtils {

    private val LOG = Logger.getInstance(GitUtils::class.java)

    private fun findRootRepo(project: Project): GitRepository? {
        val repos = GitUtil.getRepositoryManager(project).repositories
        if (repos.isEmpty()) {
            LOG.warn("Seer: No git repositories found in project")
            return null
        }

        // Prefer the repo whose root matches the project base path
        val basePath = project.basePath
        if (basePath != null) {
            val rootRepo = repos.find { it.root.path == basePath }
            if (rootRepo != null) return rootRepo
        }

        // Fallback: pick the repo with the shortest root path (most likely the root)
        return repos.minByOrNull { it.root.path.length }
    }

    fun getRemoteInfo(project: Project): RemoteInfo? {
        LOG.warn("Seer: project name='${project.name}', basePath='${project.basePath}'")
        val repo = findRootRepo(project)
        if (repo == null) return null

        LOG.warn("Seer: using repo root='${repo.root.path}'")
        val originRemote = repo.remotes.find { it.name == "origin" }
        val url = originRemote?.firstUrl
        if (url == null) {
            LOG.warn("Seer: No origin remote URL found")
            return null
        }

        return parseRemoteUrl(url)
    }

    internal fun parseRemoteUrl(url: String): RemoteInfo? {
        // SSH format: git@github.com:owner/name.git
        val sshRegex = Regex("""git@([^:]+):([^/]+)/(.+?)(?:\.git)?$""")
        sshRegex.matchEntire(url)?.let { match ->
            val host = match.groupValues[1]
            val provider = providerFromHost(host)
            return RemoteInfo(provider, match.groupValues[2], match.groupValues[3])
        }

        // HTTPS format: https://github.com/owner/name.git
        val httpsRegex = Regex("""https?://([^/]+)/([^/]+)/(.+?)(?:\.git)?$""")
        httpsRegex.matchEntire(url)?.let { match ->
            val host = match.groupValues[1]
            val provider = providerFromHost(host)
            return RemoteInfo(provider, match.groupValues[2], match.groupValues[3])
        }

        LOG.warn("Seer: Could not parse remote URL: $url")
        return null
    }

    private fun providerFromHost(host: String): String {
        return when {
            host.contains("github") -> "github"
            host.contains("gitlab") -> "gitlab"
            host.contains("bitbucket") -> "bitbucket"
            else -> "github"
        }
    }

    fun getMergeBase(project: Project, baseBranch: String = "main"): String? {
        val repo = findRootRepo(project) ?: return null

        val handler = GitLineHandler(project, repo.root, GitCommand.MERGE_BASE)
        handler.addParameters("HEAD", baseBranch)

        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) {
            result.outputAsJoinedString.trim()
        } else {
            LOG.warn("Seer: Failed to get merge-base: ${result.errorOutputAsJoinedString}")
            null
        }
    }

    fun getDiff(project: Project, baseSha: String): String? {
        val repo = findRootRepo(project) ?: return null

        // First try committed diff: baseSha..HEAD
        val committedHandler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        committedHandler.addParameters("$baseSha..HEAD")
        val committedResult = Git.getInstance().runCommand(committedHandler)
        val committedDiff = if (committedResult.success()) committedResult.outputAsJoinedString else ""

        // Also get uncommitted changes (staged + unstaged) against the base
        val workingHandler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        workingHandler.addParameters(baseSha)
        val workingResult = Git.getInstance().runCommand(workingHandler)
        val workingDiff = if (workingResult.success()) workingResult.outputAsJoinedString else ""

        // Use whichever is longer (working tree diff includes both committed + uncommitted)
        val diff = if (workingDiff.length >= committedDiff.length) workingDiff else committedDiff
        LOG.warn("Seer: committed diff length=${committedDiff.length}, working tree diff length=${workingDiff.length}, using=${diff.length}")
        return diff
    }

    fun getCommitMessages(project: Project, baseSha: String): String? {
        val repo = findRootRepo(project) ?: return null

        val handler = GitLineHandler(project, repo.root, GitCommand.LOG)
        handler.addParameters("--format=%B", "$baseSha..HEAD")

        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) {
            result.outputAsJoinedString.trim()
        } else {
            LOG.warn("Seer: Failed to get commit messages: ${result.errorOutputAsJoinedString}")
            null
        }
    }
}
