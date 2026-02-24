package io.sentry.seer.git

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

object GitHubUtils {

    private val LOG = Logger.getInstance(GitHubUtils::class.java)

    private val cache = ConcurrentHashMap<String, String>()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        expectSuccess = false
    }

    suspend fun fetchGitHubRepoId(owner: String, name: String): String? {
        val cacheKey = "$owner/$name"
        cache[cacheKey]?.let { return it }

        return try {
            val response = httpClient.get("https://api.github.com/repos/$owner/$name") {
                headers.append("Accept", "application/vnd.github.v3+json")
            }
            if (response.status.isSuccess()) {
                val json: JsonObject = response.body()
                val id = json["id"]?.jsonPrimitive?.content
                if (id != null) {
                    cache[cacheKey] = id
                }
                id
            } else {
                LOG.warn("GitHub API returned ${response.status.value} for $cacheKey")
                null
            }
        } catch (e: Exception) {
            LOG.error("Failed to fetch GitHub repo ID for $cacheKey", e)
            null
        }
    }
}
