package io.sentry.profiling

import io.sentry.logging.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profiles(
    val shared: Shared,
    val profiles: List<Profile>
)

@Serializable
data class Shared(
    val frames: List<Frame>
)

@Serializable
data class Frame(
    val file: String? = null,
    val line: Int? = null,
    @SerialName("is_application") val isApplication: Boolean = false
)

@Serializable
data class Profile(
    val samples: List<List<Int>>,
    @SerialName("sample_counts") val sampleCounts: List<Long>? = null
)

fun aggregateApplicationFrameCounts(input: Profiles): Map<String, Long> {
    data class FrameAgg(val file: String?, val line: Int?, var count: Long)

    val appFramesById = mutableMapOf<Int, FrameAgg>()
    input.shared.frames.forEachIndexed { index, frame ->
        if (frame.isApplication) {
            appFramesById[index] = FrameAgg(frame.file, frame.line, 0L)
        }
    }

    for (profile in input.profiles) {
        if (profile.sampleCounts == null) {
            Logger.error("Profiles", "missing sample counts")
            continue
        }
        val samples = profile.samples
        val counts = profile.sampleCounts
        val upper = minOf(samples.size, counts.size)
        for (i in 0 until upper) {
            val sampleCount = counts[i]
            val sample = samples[i]
            for (frameId in sample) {
                val agg = appFramesById[frameId]
                if (agg != null) {
                    agg.count += sampleCount
                }
            }
        }
    }

    val result = mutableMapOf<String, Long>()
    for (agg in appFramesById.values) {
        val key = "${agg.file}:${agg.line}"
        result[key] = (result[key] ?: 0L) + agg.count
    }
    return result
}


