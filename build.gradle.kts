plugins {
    kotlin("jvm") version libs.versions.kotlin
}

group = "org.plan.research.cachealot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ksmt.core)
    implementation(libs.ksmt.z3)
    implementation(libs.ksmt.runner)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}