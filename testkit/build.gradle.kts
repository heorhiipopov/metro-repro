plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
    systemProperty("projectRoot", rootProject.projectDir.absolutePath)
}