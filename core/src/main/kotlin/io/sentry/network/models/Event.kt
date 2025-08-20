package io.sentry.network.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
data class Event(
    val eventID: String,
    val tags: List<EventTag> = emptyList(),
    val dateCreated: String? = null,
    val user: EventUser? = null,
    val message: String? = null,
    val title: String,
    val id: String,
    val platform: String,
    val groupID: String,
    val crashFile: String? = null,
    val location: String? = null,
    val culprit: String? = null,
    val projectID: String,
    val metadata: EventMetadata? = null,
    val entries: List<EventEntry> = emptyList()
)

@Serializable
data class EventTag(
    val key: String,
    val value: String
)

@Serializable
data class EventUser(
    val id: String? = null,
    val email: String? = null,
    val username: String? = null,
    val name: String? = null
)

@Serializable
data class EventMetadata(
    val type: String? = null,
    val value: String? = null,
    val filename: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class EventEntry {

    @Serializable
    @SerialName("exception")
    data class ExceptionEventEntry(
        @SerialName("data") val data: EventEntryData,
    ) : EventEntry()

    @Serializable
    @SerialName("breadcrumbs")
    data class BreadcrumbEventEntry(
        @SerialName("data") val data: BreadcrumbEntryData,
    ) : EventEntry()

    @Serializable
    @SerialName("debugmeta")
    data class DebugMetaEventEntry(
        @SerialName("data") val data: DebugMetaEntryData,
    ) : EventEntry()

    @Serializable
    @SerialName("threads")
    data class ThreadsEventEntry(
        @SerialName("data") val data: ThreadsEntryData,
    ) : EventEntry()

    @Serializable
    @SerialName("spans")
    class SpansEventEntry(
    ) : EventEntry()
}

@Serializable
data class EventEntryData(
    val values: List<ExceptionValue> = emptyList()
)

@Serializable
data class BreadcrumbEntryData(
    val values: List<Breadcrumb> = emptyList()
)

@Serializable
data class DebugMetaEntryData(
    val images: List<DebugMeta> = emptyList()
)

@Serializable
data class ThreadsEntryData(
    val values: List<ThreadData> = emptyList()
)

@Serializable
data class ThreadData(
    val id: Int,
    val name: String? = null
)

@Serializable
data class DebugMeta(
    val type: String? = null,
    val uuid: String? = null
)

@Serializable
data class Breadcrumb(
    val type: String,
    val level: String,
    val message: String?
)

@Serializable
data class ExceptionValue(
    val type: String,
    val value: String?,
    val mechanism: ExceptionMechanism? = null,
    val threadId: Long? = null,
    val module: String? = null,
    val stacktrace: Stacktrace? = null,
    val rawStacktrace: Stacktrace? = null
)

@Serializable
data class ExceptionMechanism(
    val type: String,
    @SerialName("exception_id")
    val exceptionId: Int? = null
)

@Serializable
data class Stacktrace(
    val frames: List<StackFrame> = emptyList(),
    val framesOmitted: Int? = null,
    val registers: Map<String, String>? = null,
    val hasSystemFrames: Boolean = false
)

@Serializable
data class StackFrame(
    val filename: String? = null,
    val absPath: String? = null,
    val module: String? = null,
    @SerialName("package")
    val packageName: String? = null,
    val platform: String? = null,
    val instructionAddr: String? = null,
    val symbolAddr: String? = null,
    val function: String? = null,
    val rawFunction: String? = null,
    val symbol: String? = null,
    val lineNo: Int? = null,
    val colNo: Int? = null,
    val inApp: Boolean = false,
    val trust: String? = null,
    val errors: List<String>? = null,
    val lock: String? = null,
    val sourceLink: String? = null,
    val vars: Map<String, String>? = null
)