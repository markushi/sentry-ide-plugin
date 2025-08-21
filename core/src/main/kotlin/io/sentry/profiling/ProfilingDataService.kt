package io.sentry.profiling

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.sentry.logging.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Service(Service.Level.PROJECT)
class ProfilingDataService(private val project: Project) {

    private val profilingData = ConcurrentHashMap<String, Map<Int, Long>>()
    private val maxSampleCounts = ConcurrentHashMap<String, Long>()

    fun loadProfilingData(profiles: Profiles) {
        // TODO there's no lineno on Java
        val aggregatedData = aggregateApplicationFrameCounts(profiles)
        val fileLineData = mutableMapOf<String, MutableMap<Int, Long>>()

        // Group by file and line
        for ((fileLineKey, count) in aggregatedData) {
            val parts = fileLineKey.split(":")
            if (parts.size == 2) {
                val file = parts[0]
                val lineStr = parts[1]
                val line = lineStr.toIntOrNull()

                if (file != "null" && line != null) {
                    val fileData = fileLineData.getOrPut(file) { mutableMapOf() }
                    fileData[line] = count
                }
            }
        }

        // Calculate max sample count per file for normalization
        for ((file, lineData) in fileLineData) {
            val maxCount = lineData.values.maxOrNull() ?: 0L
            profilingData[file] = lineData.toMap()
            maxSampleCounts[file] = maxCount
        }

        Logger.debug("ProfilingDataService", "Loaded profiling data for ${fileLineData.size} files")
    }

    fun getProfilingDataForFile(file: VirtualFile): Pair<Long, Map<Int, Long>> {
        // TODO properly implement, as we do not seem to have line numbers for our JVM profiles
        val rand = Random(42)

        val rawResult = FloatArray(500)
        for (i in 0 until rawResult.size) {
            rawResult[i] = rand.nextFloat()
        }
        // dumb forward smoothing to make it look a bit prettier
        val refinedResult = mutableMapOf<Int, Long>()
        for (i in 0 until rawResult.size - 2) {
            refinedResult[i] =
                ((rawResult[i] * 0.5f + rawResult[i + 1] * 0.25f + rawResult[i + 2] * 0.125f) * 100).toLong()
        }

        val max = refinedResult.maxBy { it.value }.value
        return Pair(max, refinedResult)
    }

    companion object {
        fun getInstance(project: Project): ProfilingDataService {
            return project.getService(ProfilingDataService::class.java)
        }
    }
}