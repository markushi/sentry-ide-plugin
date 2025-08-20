package io.sentry.adb

import io.sentry.logging.Logger

private const val TAG = "DeviceWatcher"

interface SentryLogListener {
    /**
     * @param deviceId the local device id
     * @param installationId the sentry sdk installation
     */
    fun onSentryDeviceIdFound(deviceId: String, installationId: String)
    fun onDeviceConnected(deviceId: String)
    fun onDeviceDisconnected(deviceId: String)
}

class DeviceWatcher(
    private val deviceId: String,
    private val listener: SentryLogListener
) : Thread("DeviceWatcher-$deviceId") {

    @Volatile
    private var running = true

    fun stopWatching() {
        running = false
        interrupt()
    }

    override fun run() {
        try {
            val process = ProcessBuilder("adb", "-s", deviceId, "logcat")
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().use { reader ->
                while (running && !isInterrupted) {
                    val line = reader.readLine() ?: break
                    processLogLine(line)
                }
            }

            process.destroy()
        } catch (e: Exception) {
            if (running) {
                Logger.warn(TAG, "Error watching device $deviceId: ${e.message}")
            }
        }
    }

    private fun processLogLine(line: String) {
        if (line.contains("sentry.debug.installation-id=")) {
            val installationId: String = line.substringAfter("sentry.debug.installation-id=")
            listener.onSentryDeviceIdFound(deviceId, installationId)
        }
    }
}

class DeviceListWatcher(
    private val listener: SentryLogListener
) : Thread("DeviceListWatcher") {

    @Volatile
    private var running = true
    private val devices = mutableMapOf<String, DeviceWatcher>()

    fun stopWatching() {
        running = false
        devices.values.forEach { it.stopWatching() }
        interrupt()
    }

    override fun run() {
        while (running && !isInterrupted) {
            try {
                val currentDevices = getConnectedDevices()
                updateDeviceWatchers(currentDevices)
                sleep(1000) // Poll every 2 seconds
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                if (running) {
                    Logger.debug(TAG, "Error polling devices: ${e.message}")
                }
            }
        }
    }

    private fun getConnectedDevices(): Set<String> {
        return try {
            val process = ProcessBuilder("adb", "devices")
                .redirectErrorStream(true)
                .start()

            val devices = mutableSetOf<String>()
            process.inputStream.bufferedReader().use { reader ->
                reader.lines()
                    .skip(1) // Skip "List of devices attached" header
                    .forEach { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 2 && parts[1] == "device") {
                            devices.add(parts[0])
                        }
                    }
            }

            process.waitFor()
            devices
        } catch (e: Exception) {
            Logger.warn(TAG, "Error getting connected devices: ${e.message}")
            emptySet()
        }
    }

    private fun updateDeviceWatchers(currentDevices: Set<String>) {
        // Remove watchers for disconnected devices
        val disconnectedDevices = devices.keys - currentDevices
        for (deviceId in disconnectedDevices) {
            devices[deviceId]?.stopWatching()
            devices.remove(deviceId)
            Logger.debug(TAG, "Stopped watching disconnected device: $deviceId")
            listener.onDeviceDisconnected(deviceId)
        }

        // Add watchers for new devices
        val newDevices = currentDevices - devices.keys
        for (deviceId in newDevices) {
            val watcher = DeviceWatcher(deviceId, listener)
            devices[deviceId] = watcher
            watcher.start()
            Logger.debug(TAG, "Started watching new device: $deviceId")
            listener.onDeviceConnected(deviceId)
        }
    }
}
