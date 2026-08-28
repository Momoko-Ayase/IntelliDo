import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.intellij.platform.module) apply false
}

group = providers.gradleProperty("intellido.group").get()
version = providers.gradleProperty("intellido.version").get()

val intellidoChannel: String =
    providers.gradleProperty("intellido.channel").orElse("stable").get()
val isNightly = intellidoChannel == "nightly"
val platformPrefix = if (isNightly) "IntelliDoNightly" else "IntelliDo"
val pathsSelector = if (isNightly) "IntelliDoNightly" else "IntelliDo"
val visibleProductName = if (isNightly) "IntelliDo Nightly" else "IntelliDo"
// Visible author/publisher. PathManager uses idea.vendor.name as the AppData parent folder — no spaces.
val vendorDisplayName = "Momoko Ayase"
val vendorDirectoryName = "Momokko"

fun stampSplashLabels(source: File, dest: File, productName: String, version: String, buildInfo: String) {
    val image = ImageIO.read(source) ?: throw GradleException("Cannot read splash $source")
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    val baseline = image.height - 88
    graphics.color = Color.WHITE
    graphics.font = Font("SansSerif", Font.BOLD, 28)
    graphics.drawString(productName, 36, baseline)
    graphics.font = Font("SansSerif", Font.PLAIN, 16)
    graphics.color = Color(0xF6, 0xC3, 0x44)
    graphics.drawString(version, 36, baseline + 28)
    graphics.color = Color(0xDD, 0xDD, 0xDD)
    graphics.font = Font("SansSerif", Font.PLAIN, 12)
    graphics.drawString(buildInfo, 36, baseline + 50)
    graphics.dispose()
    dest.parentFile.mkdirs()
    ImageIO.write(image, "png", dest)
}

val stampBrandingSplash = tasks.register("stampBrandingSplash") {
    group = "build"
    description = "Stamp product name and version onto the window-sized splash bitmap."
    val stableIn = rootProject.file("artwork/final/splash-window.png")
    val nightlyIn = rootProject.file("artwork/final/splash-window-nightly.png")
    val outputDir = layout.buildDirectory.dir("stamped-splash")
    inputs.files(stableIn, nightlyIn)
    inputs.property("version", project.version.toString())
    outputs.dir(outputDir)
    doLast {
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val version = project.version.toString()
        val dest = outputDir.get().asFile
        stampSplashLabels(
            stableIn,
            dest.resolve("stable/splash.png"),
            "IntelliDo",
            version,
            "$version+$date",
        )
        stampSplashLabels(
            nightlyIn,
            dest.resolve("nightly/splash.png"),
            "IntelliDo Nightly",
            version,
            "$version-nightly.$date+unknown",
        )
    }
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(libs.versions.intellijPlatform.get())
        bundledModule("intellij.platform.ui.jcef")
        bundledModule("intellij.libraries.jcef")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
        jetbrainsRuntime()
    }

    implementation(project(":domain"))
    implementation(project(":transport"))
    implementation(project(":platform"))
    implementation(project(":browser"))
    implementation(project(":connect"))
    implementation(project(":ui"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

intellijPlatform {
    projectName.set(visibleProductName)
    instrumentCode.set(false)
    pluginConfiguration {
        id = "moe.momokko.intellido"
        name = visibleProductName
        version = project.version.toString()
        description.set(
            """
            <p>IntelliDo is an unofficial LINUX DO desktop client built as a standalone IntelliJ Platform product.</p>
            <p>非官方 LINUX DO 客户端 / Unofficial LINUX DO Client. It is not made, endorsed, or supported by LINUX DO.</p>
            """.trimIndent(),
        )
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
        vendor {
            name = "Momoko Ayase"
            url = "https://github.com/Momoko-Ayase/IntelliDo"
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        allWarningsAsErrors.set(true)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(stampBrandingSplash)
    filesMatching("idea/*ApplicationInfo.xml") {
        filter { line ->
            line.replace(
                "@build.date@",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")),
            )
        }
    }
    from(stampBrandingSplash.map { it.outputs.files.singleFile.resolve("stable") }) {
        into("branding/stable")
    }
    from(rootProject.file("artwork/final/icons/stable")) {
        into("branding/stable/icons")
    }
    from(rootProject.file("artwork/final/intellido.ico")) {
        into("branding/stable")
    }
    from(rootProject.file("artwork/final/icons/nightly")) {
        into("branding/nightly/icons")
    }
    from(stampBrandingSplash.map { it.outputs.files.singleFile.resolve("nightly") }) {
        into("branding/nightly")
    }
    from(rootProject.file("artwork/final/intellido-nightly.ico")) {
        into("branding/nightly")
        rename { "intellido.ico" }
    }
    from(rootProject.file("docs/jcef-repair.zh.md")) {
        into("docs")
    }
    from(rootProject.file("docs/jcef-repair.md")) {
        into("docs")
    }
}

val intellidoTransport: String =
    (System.getProperty("intellido.transport")
        ?: providers.gradleProperty("intellido.transport").orNull
        ?: "jcef")

val strippedIdeDir = layout.buildDirectory.dir("stripped-ide")

tasks.register("stripBundledPlugins") {
    group = "intellij platform"
    description = "Omit unused bundled plugins from a local IDE root used by runIde."
    doLast {
        val platform = tasks.named<RunIdeTask>("runIde").get().platformPath.toFile()
        stripBundledPlugins(platform, strippedIdeDir.get().asFile)
    }
}

tasks.withType<RunIdeTask>().configureEach {
    dependsOn("stripBundledPlugins", "processResources")
    val ideTask = this
    jvmArgs(
        "-Didea.platform.prefix=$platformPrefix",
        "-Didea.paths.selector=$pathsSelector",
        "-Didea.executable=intellido",
        "-Didea.vendor.name=$vendorDirectoryName",
        "-Didea.initially.ask.config=never",
        "-Dintellij.startup.wizard=false",
        "-Di18n.locale=zh-CN",
        "-DJetBrains.region.code=china",
        "-Duser.language=zh",
        "-Duser.country=CN",
        "-Dide.reopen.last.project=false",
        "-Didea.crash.reports.enabled=false",
        "-Dintellido.channel=$intellidoChannel",
        "-Dintellido.version=${project.version}",
        "-Dintellido.transport=$intellidoTransport",
        "-Dintellij.platform.load.app.info.from.resources=true",
    )
    doFirst {
        classpath = classpath + files(layout.buildDirectory.dir("resources/main"))
        val dest = strippedIdeDir.get().asFile
        stripBundledPlugins(ideTask.platformPath.toFile(), dest)
        val filtered = jvmArgs.filterNot {
            it.startsWith("-Didea.paths.selector=") ||
                it.startsWith("-Didea.vendor.name=") ||
                it.startsWith("-Didea.initially.ask.config=") ||
                it.startsWith("-Didea.home.path=") ||
                it.startsWith("-Dintellij.platform.load.app.info.from.resources=")
        }
        jvmArgs = filtered + listOf(
            "-Didea.paths.selector=$pathsSelector",
            "-Didea.vendor.name=$vendorDirectoryName",
            "-Didea.initially.ask.config=never",
            "-DJetBrains.region.code=china",
            "-Didea.home.path=${dest.absolutePath}",
            "-Dintellij.platform.load.app.info.from.resources=true",
        )
    }
}

tasks.withType<PrepareSandboxTask>().configureEach {
    doLast {
        val source = rootProject.file("platform/src/main/resources/ide/disabled-plugins.txt")
        val sandboxRoot = rootProject.file(".intellijPlatform/sandbox")
        if (!source.exists() || !sandboxRoot.exists()) {
            return@doLast
        }
        sandboxRoot.walkTopDown()
            .filter { it.isDirectory && it.name == "config" }
            .forEach { configDir ->
                source.copyTo(configDir.resolve("disabled_plugins.txt"), overwrite = true)
            }
    }
}

subprojects {
    configurations.configureEach {
        if (isCanBeResolved) {
            resolutionStrategy.activateDependencyLocking()
        }
    }
}

configurations.configureEach {
    if (isCanBeResolved) {
        resolutionStrategy.activateDependencyLocking()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        if (name != "ui") {
            dependencies {
                "compileOnly"(kotlin("stdlib"))
                "testImplementation"(kotlin("stdlib"))
            }
        }
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(25)
        }
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_25)
                allWarningsAsErrors.set(true)
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(25)
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

fun loadKeptPluginIds(): Set<String> {
    val file = rootProject.file("platform/src/main/resources/ide/kept-plugins.txt")
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toSet()
}

fun readPluginId(directory: File): String? {
    val marker = Regex("<id>([^<]+)</id>")
    val looseXml = directory.resolve("META-INF/plugin.xml")
    if (looseXml.isFile) {
        marker.find(looseXml.readText())?.groupValues?.get(1)?.let { return it }
    }
    val lib = directory.resolve("lib")
    val jars = buildList {
        lib.listFiles()?.filter { it.extension == "jar" }?.let { addAll(it) }
        lib.resolve("modules").listFiles()?.filter { it.extension == "jar" }?.let { addAll(it) }
    }
    for (jar in jars) {
        try {
            ZipFile(jar).use { zip ->
                val entries = zip.entries().asSequence().filter { it.name.endsWith("plugin.xml") }
                for (entry in entries) {
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    val id = marker.find(text)?.groupValues?.get(1)
                    if (id != null) {
                        return id
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
    return null
}

fun isLink(file: File): Boolean {
    if (Files.isSymbolicLink(file.toPath())) {
        return true
    }
    if (!file.isDirectory) {
        return false
    }
    return try {
        file.canonicalFile.absolutePath != file.absoluteFile.absolutePath
    } catch (_: Exception) {
        false
    }
}

fun removeLinkOrDir(file: File) {
    if (!file.exists()) {
        return
    }
    if (isLink(file)) {
        val windowsJunction = System.getProperty("os.name").lowercase().contains("windows") &&
            file.isDirectory &&
            !Files.isSymbolicLink(file.toPath())
        if (windowsJunction) {
            ProcessBuilder("cmd.exe", "/c", "rmdir", file.absolutePath)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } else {
            Files.delete(file.toPath())
        }
        return
    }
    if (file.isDirectory) {
        file.deleteRecursively()
    } else {
        file.delete()
    }
}

fun linkDirectory(target: File, link: File) {
    if (link.exists()) {
        val same = isLink(link) && try {
            link.canonicalFile == target.canonicalFile
        } catch (_: Exception) {
            false
        }
        if (same) {
            return
        }
        removeLinkOrDir(link)
    }
    link.parentFile.mkdirs()
    try {
        Files.createSymbolicLink(link.toPath(), target.toPath())
        return
    } catch (_: Exception) {
    }
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        val proc = ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.absolutePath, target.absolutePath)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() == 0) {
            return
        }
        logger.warn("mklink /J failed for ${link.name}: $out")
    }
    target.copyRecursively(link, overwrite = true)
}

@Suppress("UNCHECKED_CAST")
fun filterProductInfo(
    source: File,
    dest: File,
    keepIds: Set<String>,
    keptDirs: Set<String>,
    discoveredIds: Set<String>,
) {
    val root = JsonSlurper().parse(source) as MutableMap<String, Any?>
    val keep = keepIds + "com.intellij"
    val bundled = (root["bundledPlugins"] as? List<*>)?.filter { it in keep }
    if (bundled != null) {
        root["bundledPlugins"] = bundled
    }
    val layout = root["layout"] as? List<*>
    if (layout != null) {
        root["layout"] = layout.filter { item ->
            val map = item as? Map<*, *> ?: return@filter true
            when (map["kind"]) {
                "plugin" -> map["name"] in keep
                "pluginAlias" -> {
                    val name = map["name"] as? String ?: return@filter true
                    name !in discoveredIds || name in keep
                }
                "moduleV2" -> {
                    val classPath = map["classPath"] as? List<*> ?: return@filter true
                    classPath.all { path ->
                        val relative = path.toString().replace('\\', '/')
                        !relative.startsWith("plugins/") ||
                            keptDirs.any { relative == "plugins/$it" || relative.startsWith("plugins/$it/") }
                    }
                }
                else -> true
            }
        }
    }
    dest.writeText(JsonOutput.toJson(root))
}

fun DataInputStream.readFullyBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    readFully(bytes)
    return bytes
}

class BundledPluginRecord(val directory: String, val descriptor: ByteArray, val files: List<String>)

fun filterPluginClasspath(
    source: File,
    dest: File,
    keptDirs: Set<String>,
    extra: List<BundledPluginRecord> = emptyList(),
) {
    DataInputStream(BufferedInputStream(FileInputStream(source))).use { input ->
        val version = input.readUnsignedByte()
        val flag = input.readUnsignedByte()
        if (version != 2) {
            logger.warn("plugin-classpath.txt version $version is not 2; copying unfiltered so the core plugin still loads")
            source.copyTo(dest, overwrite = true)
            return
        }
        val coreXml = input.readFullyBytes(input.readInt())
        val kept = ArrayList<BundledPluginRecord>()
        repeat(input.readUnsignedShort()) {
            val fileCount = input.readUnsignedShort()
            val directory = input.readUTF()
            val descriptor = input.readFullyBytes(input.readInt())
            val files = List(fileCount) { input.readUTF() }
            if (directory in keptDirs) {
                kept += BundledPluginRecord(directory, descriptor, files)
            }
        }
        kept += extra
        dest.parentFile.mkdirs()
        DataOutputStream(BufferedOutputStream(FileOutputStream(dest))).use { output ->
            output.writeByte(version)
            output.writeByte(flag)
            output.writeInt(coreXml.size)
            output.write(coreXml)
            output.writeShort(kept.size)
            for (record in kept) {
                output.writeShort(record.files.size)
                output.writeUTF(record.directory)
                output.writeInt(record.descriptor.size)
                output.write(record.descriptor)
                record.files.forEach { output.writeUTF(it) }
            }
        }
        logger.lifecycle("Filtered plugin-classpath.txt to ${kept.size} bundled plugins plus IDEA CORE")
    }
}

fun stripBundledPlugins(platformHome: File, dest: File) {
    val keepIds = loadKeptPluginIds()
    val srcPlugins = File(platformHome, "plugins")
    val discovered = mutableMapOf<String, String>()
    srcPlugins.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
        val id = readPluginId(dir)
        if (id != null) {
            discovered[dir.name] = id
        }
    }
    val keptDirs = discovered.filter { it.value in keepIds }.keys
    val stamp = dest.resolve(".intellido-keep-stamp")
    val stampText = buildString {
        appendLine("v4-appinfo-no-java")
        appendLine(platformHome.canonicalPath)
        keepIds.sorted().forEach { appendLine(it) }
        keptDirs.sorted().forEach { appendLine(it) }
    }
    if (dest.resolve("plugins").isDirectory && stamp.isFile && stamp.readText() == stampText) {
        return
    }
    dest.mkdirs()
    listOf("bin", "lib", "jbr", "modules", "license", "help", "jdk-shared-indexes").forEach { name ->
        val src = File(platformHome, name)
        if (src.exists()) {
            linkDirectory(src, File(dest, name))
        }
    }
    val buildTxt = File(platformHome, "build.txt")
    if (buildTxt.isFile) {
        buildTxt.copyTo(File(dest, "build.txt"), overwrite = true)
    }
    val productInfo = File(platformHome, "product-info.json")
    if (productInfo.isFile) {
        filterProductInfo(
            productInfo,
            File(dest, "product-info.json"),
            keepIds,
            keptDirs,
            discovered.values.toSet(),
        )
    }
    val destPlugins = File(dest, "plugins")
    destPlugins.mkdirs()
    destPlugins.listFiles()?.forEach { child ->
        if (child.name !in keptDirs) {
            removeLinkOrDir(child)
        }
    }
    keptDirs.forEach { name ->
        linkDirectory(File(srcPlugins, name), File(destPlugins, name))
    }
    val pluginClasspath = File(srcPlugins, "plugin-classpath.txt")
    if (pluginClasspath.isFile) {
        val destClasspath = File(destPlugins, "plugin-classpath.txt")
        if (destClasspath.exists()) {
            removeLinkOrDir(destClasspath)
        }
        filterPluginClasspath(pluginClasspath, destClasspath, keptDirs)
    }
    stamp.writeText(stampText)
    logger.lifecycle(
        "IntelliDo omitted unused bundled plugins: keeping ${keptDirs.size} of ${discovered.size}",
    )
}

fun intellidoJvmProperties(): List<String> = listOf(
    "-Didea.platform.prefix=$platformPrefix",
    "-Didea.paths.selector=$pathsSelector",
    "-Didea.executable=intellido",
    "-Didea.vendor.name=$vendorDirectoryName",
    "-Didea.initially.ask.config=never",
    "-Dintellij.startup.wizard=false",
    "-Di18n.locale=zh-CN",
    "-DJetBrains.region.code=china",
    "-Duser.language=zh",
    "-Duser.country=CN",
    "-Dide.reopen.last.project=false",
    "-Didea.crash.reports.enabled=false",
    "-Dintellido.channel=$intellidoChannel",
    "-Dintellido.version=${project.version}",
    "-Dintellido.transport=$intellidoTransport",
    "-Dintellij.platform.load.app.info.from.resources=true",
)

fun upsertProperties(file: File, extras: Map<String, String>) {
    val lines = if (file.isFile) file.readLines().toMutableList() else mutableListOf()
    extras.forEach { (key, value) ->
        val prefix = "$key="
        val index = lines.indexOfFirst { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith(prefix) && !trimmed.startsWith("#")
        }
        if (index >= 0) {
            lines[index] = "$key=$value"
        } else {
            lines.add("$key=$value")
        }
    }
    file.writeText(lines.joinToString("\n").trimEnd() + "\n")
}

fun robocopyTree(source: File, dest: File, extraArgs: List<String> = emptyList()) {
    dest.mkdirs()
    val command = mutableListOf(
        "cmd.exe", "/c", "robocopy",
        source.absolutePath,
        dest.absolutePath,
        "/E", "/COPY:DAT", "/R:1", "/W:1", "/MT:8",
        "/NFL", "/NDL", "/NJH", "/NJS", "/NC", "/NS",
    )
    command.addAll(extraArgs)
    val proc = ProcessBuilder(command).redirectErrorStream(true).start()
    val out = proc.inputStream.bufferedReader().readText()
    val code = proc.waitFor()
    if (code >= 8) {
        throw GradleException("robocopy failed ($code) ${source.name} -> ${dest.name}: $out")
    }
}

fun pluginXmlBytes(pluginDir: File): ByteArray {
    val loose = pluginDir.resolve("META-INF/plugin.xml")
    if (loose.isFile) {
        return loose.readBytes()
    }
    val jars = pluginDir.resolve("lib").listFiles()?.filter { it.extension == "jar" } ?: emptyList()
    for (jar in jars) {
        ZipFile(jar).use { zip ->
            val entry = zip.getEntry("META-INF/plugin.xml")
            if (entry != null) {
                return zip.getInputStream(entry).readBytes()
            }
        }
    }
    throw GradleException("plugin.xml not found under $pluginDir")
}

@Suppress("UNCHECKED_CAST")
fun brandWindowsProductInfo(
    source: File,
    dest: File,
    keepIds: Set<String>,
    keptDirs: Set<String>,
    discoveredIds: Set<String>,
    pluginClassPath: List<String>,
) {
    filterProductInfo(source, dest, keepIds, keptDirs, discoveredIds)
    val root = JsonSlurper().parse(dest) as MutableMap<String, Any?>
    root["name"] = visibleProductName
    root["version"] = project.version.toString()
    root["productVendor"] = "Momoko Ayase"
    root["envVarBaseName"] = "INTELLIDO"
    root["dataDirectoryName"] = pathsSelector
    root["svgIconPath"] = "bin/intellido.svg"
    root["fileExtensions"] = emptyList<Any>()
    val bundled = ArrayList<Any?>()
    (root["bundledPlugins"] as? List<*>)?.forEach { bundled.add(it) }
    if ("moe.momokko.intellido" !in bundled) {
        bundled.add("moe.momokko.intellido")
    }
    root["bundledPlugins"] = bundled
    val layout = ArrayList<Any?>()
    (root["layout"] as? List<*>)?.forEach { item ->
        val map = item as? Map<*, *>
        if (map?.get("name") != "moe.momokko.intellido") {
            layout.add(item)
        }
    }
    layout.add(
        mapOf(
            "name" to "moe.momokko.intellido",
            "kind" to "plugin",
            "classPath" to pluginClassPath,
        ),
    )
    root["layout"] = layout
    val launch = ArrayList<Any?>()
    (root["launch"] as? List<*>)?.forEach { item ->
        val map = ((item as? Map<*, *>) ?: emptyMap<Any?, Any?>()).toMutableMap()
        map["launcherPath"] = "bin/intellido64.exe"
        map["vmOptionsFilePath"] = "bin/intellido64.exe.vmoptions"
        map.remove("customCommands")
        val args = ArrayList<String>()
        (map["additionalJvmArguments"] as? List<*>)?.map { it.toString() }?.let { args.addAll(it) }
        fun replacePrefixed(prefix: String, value: String) {
            args.removeAll { it.startsWith(prefix) }
            args.add(value)
        }
        intellidoJvmProperties().forEach { property ->
            replacePrefixed(property.substringBefore("=") + "=", property)
        }
        map["additionalJvmArguments"] = args
        val boots = ArrayList<String>()
        (map["bootClassPathJarNames"] as? List<*>)?.map { it.toString() }?.let { boots.addAll(it) }
        boots.remove("intellido-branding.jar")
        boots.add(0, "intellido-branding.jar")
        map["bootClassPathJarNames"] = boots
        launch.add(map)
    }
    root["launch"] = launch
    dest.writeText(JsonOutput.toJson(root))
}

val windowsDistDir = layout.buildDirectory.dir("dist/windows/IntelliDo")
val windowsBrandingJar = tasks.register<Jar>("windowsBrandingJar") {
    group = "distribution"
    description = "Jar containing IntelliDo ApplicationInfo and artwork for the Windows product classpath."
    dependsOn("processResources")
    archiveFileName.set("intellido-branding.jar")
    destinationDirectory.set(layout.buildDirectory.dir("packaging"))
    from(layout.buildDirectory.dir("resources/main")) {
        include("idea/**")
        include("branding/**")
    }
}

tasks.register("materializeWindowsDist") {
    group = "distribution"
    description = "Materialize a self-contained Windows IntelliDo product tree."
    dependsOn("composedJar", "patchPluginXml", windowsBrandingJar)
    dependsOn(subprojects.map { "${it.path}:jar" })
    dependsOn(":ui:composedJar")
    val distDir = windowsDistDir
    val brandingJar = windowsBrandingJar.flatMap { it.archiveFile }
    val composedJarTask = tasks.named("composedJar")
    inputs.file(brandingJar)
    inputs.files(composedJarTask)
    inputs.files(configurations.runtimeClasspath)
    outputs.dir(distDir)
    doLast {
        val platformHome = tasks.named<RunIdeTask>("runIde").get().platformPath.toFile()
        if (!platformHome.isDirectory) {
            throw GradleException("IntelliJ platform is not downloaded at $platformHome")
        }
        val dest = distDir.get().asFile
        val keepIds = loadKeptPluginIds()
        val srcPlugins = File(platformHome, "plugins")
        val discovered = mutableMapOf<String, String>()
        srcPlugins.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val id = readPluginId(dir)
            if (id != null) {
                discovered[dir.name] = id
            }
        }
        val keptDirs = discovered.filter { it.value in keepIds }.keys
        val stamp = dest.resolve(".intellido-windows-dist-stamp")
        val stampText = buildString {
            appendLine("v1-windows-dist")
            appendLine(platformHome.canonicalPath)
            appendLine(project.version)
            keepIds.sorted().forEach { appendLine(it) }
            keptDirs.sorted().forEach { appendLine(it) }
        }
        val copied = dest.resolve("lib").isDirectory && stamp.isFile && stamp.readText() == stampText
        if (!copied) {
            dest.mkdirs()
            logger.lifecycle("Copying IntelliJ platform into $dest")
            listOf("bin", "lib", "jbr", "modules", "license").forEach { name ->
                val src = File(platformHome, name)
                if (src.exists()) {
                    robocopyTree(src, File(dest, name))
                }
            }
            File(platformHome, "build.txt").takeIf { it.isFile }?.copyTo(File(dest, "build.txt"), overwrite = true)
            val destPlugins = File(dest, "plugins")
            destPlugins.mkdirs()
            destPlugins.listFiles()?.forEach { child ->
                if (child.name != "IntelliDo" && child.name != "plugin-classpath.txt" && child.name !in keptDirs) {
                    removeLinkOrDir(child)
                }
            }
            keptDirs.forEach { name ->
                robocopyTree(File(srcPlugins, name), File(destPlugins, name))
            }
            stamp.writeText(stampText)
        }

        val bin = dest.resolve("bin")
        listOf(
            "idea.exe",
            "jetbrains_client64.exe",
            "jetbrains_client64.exe.vmoptions",
            "remote-dev-server.exe",
            "idea.bat",
            "inspect.bat",
            "format.bat",
            "ltedit.bat",
        ).forEach { name ->
            dest.resolve("bin").resolve(name).takeIf { it.exists() }?.delete()
        }
        val idea64 = bin.resolve("idea64.exe")
        val intellido64 = bin.resolve("intellido64.exe")
        if (idea64.isFile) {
            if (intellido64.exists()) {
                intellido64.delete()
            }
            if (!idea64.renameTo(intellido64)) {
                idea64.copyTo(intellido64, overwrite = true)
                idea64.delete()
            }
        }
        val srcVm = bin.resolve("idea64.exe.vmoptions")
        val destVm = bin.resolve("intellido64.exe.vmoptions")
        if (srcVm.isFile) {
            val text = srcVm.readText()
            val extras = intellidoJvmProperties().filter { property ->
                property.substringBefore("=") !in text
            }
            destVm.writeText(buildString {
                append(text)
                if (!text.endsWith("\n")) append('\n')
                extras.forEach { append(it).append('\n') }
            })
        }
        bin.resolve("intellido.bat").writeText(
            """
            @echo off
            "%~dp0intellido64.exe" %*
            """.trimIndent() + "\n",
        )
        val ico = rootProject.file("artwork/final/intellido.ico")
        val svg = rootProject.file("artwork/final/icons/stable/icon.svg")
        if (ico.isFile) {
            ico.copyTo(bin.resolve("intellido.ico"), overwrite = true)
            ico.copyTo(bin.resolve("idea.ico"), overwrite = true)
        }
        if (svg.isFile) {
            svg.copyTo(bin.resolve("intellido.svg"), overwrite = true)
            svg.copyTo(bin.resolve("idea.svg"), overwrite = true)
        }
        val rcedit = rootProject.file("build/packaging-tools/rcedit-x64.exe")
        if (rcedit.isFile && intellido64.isFile) {
            val proc = ProcessBuilder(
                rcedit.absolutePath,
                intellido64.absolutePath,
                "--set-icon", bin.resolve("intellido.ico").absolutePath,
                "--set-version-string", "ProductName", visibleProductName,
                "--set-version-string", "FileDescription", visibleProductName,
                "--set-version-string", "CompanyName", vendorDisplayName,
                "--set-version-string", "LegalCopyright", "Copyright 2026 Momoko Ayase",
                "--set-product-version", project.version.toString(),
                "--set-file-version", project.version.toString(),
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            if (proc.waitFor() != 0) {
                logger.warn("rcedit failed; installer shortcuts still use intellido.ico: $out")
            }
        }
        val properties = bin.resolve("idea.properties")
        if (properties.isFile) {
            upsertProperties(
                properties,
                mapOf(
                    "idea.paths.selector" to pathsSelector,
                    "idea.vendor.name" to vendorDirectoryName,
                    "idea.initially.ask.config" to "never",
                ),
            )
        }

        val pluginDest = dest.resolve("plugins/IntelliDo")
        if (pluginDest.exists()) {
            pluginDest.deleteRecursively()
        }
        val pluginLib = pluginDest.resolve("lib")
        pluginLib.mkdirs()
        val composed = composedJarTask.get().outputs.files.singleFile
        if (!composed.isFile) {
            throw GradleException("Plugin jar missing at $composed")
        }
        composed.copyTo(pluginLib.resolve("intellido-${project.version}.jar"), overwrite = true)
        configurations.runtimeClasspath.get().files
            .filter { it.extension == "jar" && it.isFile }
            .forEach { jar ->
                jar.copyTo(pluginLib.resolve(jar.name), overwrite = true)
            }
        val branding = brandingJar.get().asFile
        branding.copyTo(dest.resolve("lib/intellido-branding.jar"), overwrite = true)
        rootProject.file("LICENSE").copyTo(dest.resolve("IntelliDo-LICENSE.txt"), overwrite = true)
        rootProject.file("NOTICE").copyTo(dest.resolve("IntelliDo-NOTICE.txt"), overwrite = true)
        dest.resolve("LOCAL-TEST.txt").writeText(
            """
            IntelliDo ${project.version} local Windows test build
            Not a GitHub Actions release (ADR 0052).
            Channel: $intellidoChannel
            Platform: ${platformHome.name}
            """.trimIndent() + "\n",
        )

        val pluginJars = pluginDest.resolve("lib").listFiles()
            ?.filter { it.extension == "jar" }
            ?.map { "plugins/IntelliDo/lib/${it.name}" }
            ?.sorted()
            ?: throw GradleException("IntelliDo plugin jars missing under $pluginDest")
        val productInfo = File(platformHome, "product-info.json")
        brandWindowsProductInfo(
            productInfo,
            dest.resolve("product-info.json"),
            keepIds,
            keptDirs,
            discovered.values.toSet(),
            pluginJars,
        )
        val pluginRecord = BundledPluginRecord(
            directory = "IntelliDo",
            descriptor = pluginXmlBytes(pluginDest),
            files = pluginDest.resolve("lib").listFiles()
                ?.filter { it.extension == "jar" }
                ?.map { "lib/${it.name}" }
                ?.sorted()
                ?: emptyList(),
        )
        val pluginClasspath = File(srcPlugins, "plugin-classpath.txt")
        if (pluginClasspath.isFile) {
            filterPluginClasspath(
                pluginClasspath,
                dest.resolve("plugins/plugin-classpath.txt"),
                keptDirs,
                listOf(pluginRecord),
            )
        }
        if (!intellido64.isFile) {
            throw GradleException("Windows launcher missing: $intellido64")
        }
        if (!dest.resolve("lib/intellido-branding.jar").isFile) {
            throw GradleException("Branding jar was not copied into the Windows tree")
        }
        logger.lifecycle("Windows product tree ready at $dest")
    }
}

tasks.register<Zip>("packageWindowsZip") {
    group = "distribution"
    description = "Zip archive of the Windows IntelliDo product tree (config still uses per-user OS directories)."
    dependsOn("materializeWindowsDist")
    archiveFileName.set("IntelliDo-${project.version}-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist/windows"))
    from(windowsDistDir) {
        exclude("**/.intellido-windows-dist-stamp")
    }
    into("IntelliDo")
}

tasks.register("packageWindows") {
    group = "distribution"
    description = "Build the per-user Windows installer (and zip) for local install testing."
    dependsOn("materializeWindowsDist", "packageWindowsZip")
    val distDir = windowsDistDir
    doLast {
        val tools = rootProject.file("build/packaging-tools")
        val iscc = tools.resolve("innosetup/ISCC.exe")
        if (!iscc.isFile) {
            val installer = tools.resolve("innosetup-6.7.3.exe")
            if (!installer.isFile) {
                throw GradleException("Inno Setup is missing at $installer")
            }
            logger.lifecycle("Installing Inno Setup into $tools/innosetup")
            val proc = ProcessBuilder(
                installer.absolutePath,
                "/VERYSILENT",
                "/CURRENTUSER",
                "/NORESTART",
                "/NOCANCEL",
                "/SUPPRESSMSGBOXES",
                "/NOICONS",
                "/DIR=${tools.resolve("innosetup").absolutePath}",
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            if (proc.waitFor() != 0 || !iscc.isFile) {
                throw GradleException("Inno Setup silent install failed: $out")
            }
        }
        val outputDir = layout.buildDirectory.dir("dist/windows").get().asFile
        outputDir.mkdirs()
        val iss = rootProject.file("packaging/windows/IntelliDo.iss")
        val command = listOf(
            iscc.absolutePath,
            "/Qp",
            "/DMyAppName=$visibleProductName",
            "/DMyAppVersion=${project.version}",
            "/DDistDir=${distDir.get().asFile.absolutePath}",
            "/DOutputDir=${outputDir.absolutePath}",
            "/DSetupIcon=${rootProject.file("artwork/final/intellido.ico").absolutePath}",
            "/DNoticeFile=${rootProject.file("packaging/windows/INSTALL-NOTICE.zh.txt").absolutePath}",
            "/DLicenseFilePath=${rootProject.file("LICENSE").absolutePath}",
            iss.absolutePath,
        )
        logger.lifecycle("Compiling Windows installer")
        val proc = ProcessBuilder(command).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() != 0) {
            throw GradleException("ISCC failed: $out")
        }
        val exe = outputDir.resolve("IntelliDo-${project.version}-windows-x64.exe")
        if (!exe.isFile) {
            throw GradleException("Installer missing at $exe\n$out")
        }
        val md = MessageDigest.getInstance("SHA-256")
        exe.inputStream().buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        val sha = md.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        outputDir.resolve("${exe.name}.sha256").writeText("$sha  ${exe.name}\n")
        logger.lifecycle("Windows installer: ${exe.absolutePath}")
        logger.lifecycle("SHA-256: $sha")
    }
}

