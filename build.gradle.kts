plugins {
    kotlin("jvm") version libs.versions.kotlin
    application
}

val outputDir: String by project
val benchmarkDir: String by project

group = "org.plan.research.cachealot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.ksmt.core)
    implementation(libs.ksmt.z3)
    implementation(libs.ksmt.runner)

    implementation(libs.kotlinx.dataframe)

    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
}

application {
    applicationDefaultJvmArgs = listOf("-Xmx24G", "-Xms24G", "-DlogDir=$outputDir")
    mainClass = "org.plan.research.cachealot.scripts.cache.Cache_stats_gatheringKt"
    executableDir = project.rootDir.absolutePath
}

tasks {
    test {
        useJUnitPlatform()
    }

    getByName<JavaExec>("run") {
        args(benchmarkDir, outputDir)
    }
}

kotlin {
    jvmToolchain(17)
}
