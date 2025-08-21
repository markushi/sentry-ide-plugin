package io.sentry.network

import io.sentry.logging.Logger
import io.sentry.network.client.SentryApiClient
import io.sentry.network.client.SentryApiException
import kotlinx.coroutines.runBlocking

private const val TAG = "ApiDemo"

fun main() {
    // Enable debug io.sentry.logging to see raw HTTP responses
    Logger.setDebugEnabled(true)

    val token = System.getenv("SENTRY_TOKEN") ?: run {
        Logger.error(TAG, "Please set SENTRY_TOKEN environment variable")
        return
    }

    val client = SentryApiClient(token)

    runBlocking {
        try {
            Logger.info(TAG, "Fetching organizations...")
            val organizations = client.getOrganizations()
            Logger.info(TAG, "Found ${organizations.size} organizations:")
            organizations.forEach { org ->
                Logger.info(TAG, "  - ${org.name} (${org.slug})")
            }

            Logger.info(TAG, "Fetching all projects...")
            val projects = client.getProjects()
            Logger.info(TAG, "Found ${projects.size} projects:")
            projects.forEach { project ->
                Logger.info(TAG, "  - ${project.name} (${project.slug}) - Platform: ${project.platform}")
            }

            organizations.forEach { organization ->
                val orgProjects = client.getOrganizationProjects(organizations.first().slug)
                Logger.info(TAG, "Found ${orgProjects.size} projects in ${organizations.first().name}:")

                if (orgProjects.isNotEmpty()) {
                    for (project in orgProjects) {
                        Logger.info(TAG, "Processing project: ${project.name} (${project.slug})")

                        Logger.info(TAG, "Fetching issues...")
                        val issues = client.getOrgIssues(organization.slug, project.id, statsPeriod = "24h")
                        issues.forEach { issue ->
                            Logger.info(TAG, " --- ${issue.title}")
                        }

                        Logger.info(TAG, "Fetching releases...")
                        val releases = client.getProjectReleases(
                            orgSlug = organizations.first().slug,
                            projectId = project.id,
                            perPage = 10
                        )
                        Logger.info(TAG, "Found ${releases.size} releases for ${project.name}:")
                        releases.forEach { release ->
                            Logger.info(
                                TAG,
                                "  - ${release.version} (${release.dateCreated}) - ${release.commitCount} commits"
                            )
                        }

                        if (releases.isNotEmpty()) {
                            Logger.info(TAG, "Fetching release statistics...")
                            val releaseVersions = releases.map { it.version } // Take first 5 releases
                            val stats = client.getReleaseStatistics(
                                orgSlug = organizations.first().slug,
                                projectId = project.id,
                                releases = releaseVersions,
                                statsPeriod = "14d",
                                perPage = 10
                            )
                            Logger.info(
                                TAG,
                                "Found statistics for ${stats.data.size} releases (dataset: ${stats.meta.dataset}):"
                            )
                            stats.data.forEach { stat ->
                                Logger.info(TAG, "  - ${stat.release}: ${stat.count.toInt()} events")
                            }
                        }
                    }

                }
            }

        } catch (e: SentryApiException.AuthenticationException) {
            Logger.error(TAG, "Authentication failed: ${e.message}")
        } catch (e: SentryApiException.RateLimitException) {
            Logger.warn(TAG, "Rate limit exceeded: ${e.message}")
            e.retryAfter?.let { Logger.warn(TAG, "Retry after: ${it}s") }
        } catch (e: SentryApiException) {
            Logger.error(TAG, "API error: ${e.message}", e)
        } finally {
            client.close()
        }
    }
}