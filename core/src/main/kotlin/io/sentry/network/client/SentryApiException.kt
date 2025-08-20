package io.sentry.network.client

import io.ktor.client.plugins.*
import io.ktor.http.*

sealed class SentryApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkException(message: String, cause: Throwable?) : SentryApiException(message, cause)
    class AuthenticationException(message: String) : SentryApiException(message)
    class RateLimitException(message: String, val retryAfter: Long? = null) : SentryApiException(message)
    class ServerException(message: String, val statusCode: HttpStatusCode) : SentryApiException(message)
    class ClientException(message: String, val statusCode: HttpStatusCode) : SentryApiException(message)
    class UnknownException(message: String, cause: Throwable?) : SentryApiException(message, cause)
}

fun toSentryApiException(message: String, cause: Throwable?): SentryApiException {
    return when (cause) {
        is ClientRequestException -> {
            when (cause.response.status) {
                HttpStatusCode.Unauthorized -> SentryApiException.AuthenticationException("Invalid or expired token")
                HttpStatusCode.TooManyRequests -> {
                    val retryAfter = cause.response.headers["Retry-After"]?.toLongOrNull()
                    SentryApiException.RateLimitException("Rate limit exceeded", retryAfter)
                }

                else -> SentryApiException.ClientException(message, cause.response.status)
            }
        }

        is ServerResponseException -> {
            SentryApiException.ServerException(message, cause.response.status)
        }

        else -> SentryApiException.NetworkException(message, cause)
    }
}