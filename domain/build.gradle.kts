import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.kotlin.jvm)
}

val vendorLock = rootProject.file("scripts/vendor-lock.json")
val vendorScript = rootProject.file("scripts/vendor-icon-assets.py")
val vendorOutput = layout.buildDirectory.dir("generated/resources")
val vendorCache = rootProject.layout.projectDirectory.dir("tmp/vendor-dl")

fun pythonLauncher(): List<String> {
    val candidates = listOf(
        listOf("python3"),
        listOf("python"),
        listOf("py", "-3"),
    )
    candidates.forEach { command ->
        try {
            val proc = ProcessBuilder(command + "--version").redirectErrorStream(true).start()
            if (proc.waitFor() == 0) {
                return command
            }
        } catch (_: Exception) {
        }
    }
    throw GradleException("Python 3 is required to fetch Font Awesome and Twemoji assets.")
}

val vendorIconAssets = tasks.register<Exec>("vendorIconAssets") {
    group = "build"
    description = "Download pinned Font Awesome and Twemoji archives, then generate classpath resources."
    workingDir = rootProject.projectDir
    inputs.files(vendorScript, vendorLock)
    outputs.dir(vendorOutput)
    doFirst {
        val output = vendorOutput.get().asFile
        output.mkdirs()
        vendorCache.asFile.mkdirs()
        commandLine(
            pythonLauncher() + listOf(
                vendorScript.absolutePath,
                "--lock",
                vendorLock.absolutePath,
                "--cache",
                vendorCache.asFile.absolutePath,
                "--output",
                output.absolutePath,
            ),
        )
    }
}

sourceSets {
    named("main") {
        resources.srcDir(vendorOutput)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(vendorIconAssets)
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.jspecify)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
