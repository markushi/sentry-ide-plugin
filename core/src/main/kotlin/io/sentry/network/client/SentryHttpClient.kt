package io.sentry.network.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object SentryHttpClient {

    private const val TAG = "Http"

    fun create(token: String): HttpClient {

        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }


            install(DefaultRequest) {
                headers.append("Authorization", "Bearer $token")
                headers.append("Content-Type", "application/json")
            }

            // Response observer to log raw responses
            expectSuccess = false
        }
    }
}