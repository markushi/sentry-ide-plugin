package io.sentry.logging

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

object Logger {
    private var enableDebugLogging: Boolean = System.getProperty("sentry.debug") == "true"

    fun debug(tag: String, message: String) {
        if (enableDebugLogging) {
            log(LogLevel.DEBUG, tag, message)
        }
    }

    fun info(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
    }

    fun warn(tag: String, message: String) {
        log(LogLevel.WARN, tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message)
        throwable?.let {
            log(LogLevel.ERROR, tag, it.message ?: "")
            throwable.printStackTrace(System.err)
        }
    }

    fun setDebugEnabled(enabled: Boolean) {
        enableDebugLogging = enabled
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        val levelStr = level.name.padEnd(5)
        val tagStr = if (tag.length > 5) {
            tag.take(8)
        } else tag.padEnd(8)
        if (level == LogLevel.ERROR) {
            System.err.println("$timestamp [$levelStr] [$tagStr] $message")
        } else {
            println("$timestamp [$levelStr] [$tagStr] $message")
        }

    }
}