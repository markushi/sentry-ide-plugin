package io.sentry.seer.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import com.intellij.openapi.diagnostic.Logger
import io.sentry.seer.models.SeerAnalysisRequest
import io.sentry.seer.models.SeerPollResponse
import io.sentry.seer.models.SeerSubmitResponse
import kotlinx.serialization.json.Json

class SeerApiClient(private val baseUrl: String = "http://localhost:9091") {

    companion object {
        private val LOG = Logger.getInstance(SeerApiClient::class.java)
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        install(DefaultRequest) {
            headers.append("Content-Type", "application/json")
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }

        followRedirects = true
        expectSuccess = false
    }

    suspend fun submitAnalysis(request: SeerAnalysisRequest): SeerSubmitResponse {
        val url = "$baseUrl/v1/automation/codegen/pr-review-local"
        LOG.warn("Seer: submitting analysis to $url")
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            LOG.warn("Seer: submit failed: status=${response.status.value}, location=${response.headers["Location"]}, body=$body")
            throw SeerApiException("Submit failed with status ${response.status.value}: $body")
        }
        return response.body()
    }

    suspend fun pollResults(runId: Int): SeerPollResponse {
        val url = "$baseUrl/v1/automation/codegen/pr-review-local/$runId"
        LOG.warn("Seer: polling $url")
        val response = httpClient.get(url)
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            LOG.warn("Seer: poll failed: status=${response.status.value}, body=$body")
            throw SeerApiException("Poll failed with status ${response.status.value}: $body")
        }
        return response.body()
    }

    fun close() {
        httpClient.close()
    }
}

class SeerApiException(message: String) : Exception(message)
