import java.util.*

plugins {
    id("ijp-plugin-base")
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.intelliJModule)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
}

repositories {
    google()
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    mavenCentral()

    intellijPlatform { defaultRepositories() }
}

val rootProperties = Properties()
rootProject.file("gradle.properties").inputStream().use { rootProperties.load(it) }

dependencies {
    testImplementation(libs.junit)

    intellijPlatform {
        create(
            rootProperties.getProperty("platformType"),
            rootProperties.getProperty("platformVersion"),
        )

        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.jewel.ideLafBridge")
        bundledModule("intellij.libraries.compose.foundation.desktop")
        bundledModule("intellij.libraries.skiko")

        implementation("androidx.datastore:datastore:1.1.7")
        implementation("androidx.datastore:datastore-preferences:1.1.7")

        implementation("io.ktor:ktor-utils:3.2.3")
        implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
        implementation("io.ktor:ktor-client-core:2.3.12")
        implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.2.3")
        implementation("io.ktor:ktor-client-core-jvm:3.2.3")
        implementation("io.ktor:ktor-client-java:2.3.12")
        implementation("io.ktor:ktor-client-cio:3.2.3")
        implementation("io.ktor:ktor-client-resources:2.3.12")
        implementation("io.ktor:ktor-client-logging:2.3.12")
        implementation("io.ktor:ktor-client-plugins:3.1.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    }
}

configurations.all {
    resolutionStrategy {
        // as they're part of the ide platform and only produce class loader headaches otherwise
        exclude("org.jetbrains.kotlinx", module = "kotlinx-coroutines-slf4j")
        exclude("org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
        exclude("org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }
}

tasks.test { useJUnitPlatform() }
