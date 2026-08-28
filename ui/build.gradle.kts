import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.intellij.platform.module")
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
        jetbrainsRuntime()
    }

    implementation(project(":domain"))
    implementation(project(":transport"))
    implementation(project(":platform"))
    implementation(project(":browser"))
    implementation(project(":connect"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("artwork/final/icons/stable")) {
        into("branding/stable/icons")
    }
    from(rootProject.file("artwork/final/splash.png")) {
        into("branding/stable")
    }
    from(rootProject.file("artwork/final/icons/nightly")) {
        into("branding/nightly/icons")
    }
    from(rootProject.file("artwork/final/splash-nightly.png")) {
        into("branding/nightly")
        rename { "splash.png" }
    }
    from(rootProject.file("docs/jcef-repair.zh.md")) {
        into("docs")
    }
    from(rootProject.file("docs/jcef-repair.md")) {
        into("docs")
    }
}
