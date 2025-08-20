import java.util.*

plugins {
    kotlin("jvm")
}

val rootProperties = Properties()

rootProject.file("gradle.properties").inputStream().use { rootProperties.load(it) }

group = rootProperties.getProperty("pluginGroup")
version = rootProperties.getProperty("pluginVersion")

kotlin { jvmToolchain(rootProperties.getProperty("jdk.level").toInt()) }
