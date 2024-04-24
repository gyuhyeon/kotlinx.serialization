/*
 * Copyright 2017-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.knit.*
import kotlinx.validation.*
import org.jetbrains.dokka.gradle.*
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

buildscript {
    /**
     * Overrides for Teamcity 'K2 User Projects' + 'Aggregate build / Kotlinx libraries compilation' configuration:
     * kotlin_repo_url - local repository with snapshot Kotlin compiler
     * kotlin_version - kotlin version to use
     * kotlin_language_version - LV to use
     */
    val snapshotRepoUrl = rootProject.properties["kotlin_repo_url"]
    val kotlin_lv_override = rootProject.properties["kotlin_language_version"]

    extra["snapshotRepoUrl"] = snapshotRepoUrl
    extra["kotlin_lv_override"] = kotlin_lv_override

    if (snapshotRepoUrl != null && snapshotRepoUrl != "") {
        extra["kotlin_version"] = rootProject.properties["kotlin_version"]
        repositories {
            maven(snapshotRepoUrl)
        }
    } else if (project.hasProperty("bootstrap")) {
        extra["kotlin_version"] = property("kotlin.version.snapshot")
        extra["kotlin.native.home"] = System.getenv("KONAN_LOCAL_DIST")
    } else {
        extra["kotlin_version"] = property("kotlin.version")
    }
    if (project.hasProperty("library.version")) {
        extra["overriden_version"] = property("library.version")
    }

    val noTeamcityInteractionFlag = rootProject.hasProperty("no_teamcity_interaction")
    val buildSnapshotUPFlag = rootProject.hasProperty("build_snapshot_up")
    extra["teamcityInteractionDisabled"] = noTeamcityInteractionFlag || buildSnapshotUPFlag

    /*
    * This property group is used to build kotlinx.serialization against Kotlin compiler snapshot.
    * When build_snapshot_train is set to true, kotlin_version property is overridden with kotlin_snapshot_version.
    * DO NOT change the name of these properties without adapting kotlinx.train build chain.
    */
    val prop = rootProject.properties["build_snapshot_train"]
    val build_snapshot_train = prop != null && prop != ""

    extra["build_snapshot_train"] = build_snapshot_train
    if (build_snapshot_train) {
        val kotlin_version = rootProject.properties["kotlin_snapshot_version"]
        extra["kotlin_version"] = kotlin_version
        if (kotlin_version == null) {
            throw IllegalArgumentException("\"kotlin_snapshot_version\" should be defined when building with snapshot compiler")
        }
        repositories {
            maven("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }

    repositories {
        maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
        // kotlin-dev with space redirector
        maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        mavenCentral()
        gradlePluginPortal()
        // For Dokka that depends on kotlinx-html
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
        mavenLocal()
    }

    configurations.classpath {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(rootProject.extra["kotlin_version"] as String)
            }
        }
    }

    val kotlin_version = rootProject.extra["kotlin_version"] as String

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlin_version")
        classpath("org.jetbrains.kotlinx:binary-compatibility-validator:${property("validator_version")}")
        classpath("org.jetbrains.kotlinx:kotlinx-knit:${property("knit_version")}")
        classpath("ru.vyarus:gradle-animalsniffer-plugin:1.7.1") // Android API check)

        classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.0")

        // Various benchmarking stuff
        classpath("com.github.johnrengelman:shadow:8.1.1")
        classpath("me.champeau.jmh:jmh-gradle-plugin:0.7.2")
    }
}

val experimentalsEnabled = listOf(
    "-progressive",
    "-opt-in=kotlin.ExperimentalMultiplatform",
    "-opt-in=kotlinx.serialization.InternalSerializationApi",
    "-P", "plugin:org.jetbrains.kotlinx.serialization:disableIntrinsic=false"
)

val experimentalsInTestEnabled = listOf(
    "-progressive",
    "-opt-in=kotlin.ExperimentalMultiplatform",
    "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
    "-opt-in=kotlinx.serialization.InternalSerializationApi",
    "-P", "plugin:org.jetbrains.kotlinx.serialization:disableIntrinsic=false"
)

// To make it visible for compiler-version.gradle
extra["compilerVersion"] = org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION
extra["nativeDebugBuild"] = org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG

apply(plugin = "binary-compatibility-validator")
apply(plugin = "base")
apply(plugin = "kotlinx-knit")

extensions.configure<ApiValidationExtension> {
    ignoredProjects.addAll(listOf("benchmark", "guide", "kotlinx-serialization"))
    klib {
        enabled = true
    }
}

extensions.configure<KnitPluginExtension> {
    siteRoot = "https://kotlinlang.org/api/kotlinx.serialization"
    moduleDocs = "build/dokka/htmlMultiModule"
}

// Build API docs for all modules with dokka before running Knit
tasks.named("knitPrepare") {
    dependsOn("dokka")
}


allprojects {
    group = "org.jetbrains.kotlinx"

    val deployVersion = properties["DeployVersion"]
    if (deployVersion != null) version = deployVersion

    if (project.hasProperty("bootstrap")) {
        version = "$version-SNAPSHOT"
    }

    // the only place where HostManager could be instantiated
    project.extra["hostManager"] = org.jetbrains.kotlin.konan.target.HostManager()

    if (rootProject.extra["build_snapshot_train"] == true) {
        // Snapshot-specific
        repositories {
            mavenLocal()
            maven("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }

    val snapshotRepoUrl = rootProject.properties["kotlin_repo_url"]
    if (snapshotRepoUrl != null && snapshotRepoUrl != "") {
        // Snapshot-specific for K2 CI configurations
        repositories {
            maven(snapshotRepoUrl)
        }
    }

    repositories {
        mavenCentral()
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(rootProject.extra["kotlin_version"] as String)
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
    // kotlin-dev with space redirector
    maven("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    // For Dokka that depends on kotlinx-html
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    // For local development
    mavenLocal()

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>().configureEach {
    compilerOptions { freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR") }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    compilerOptions { freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR") }
}

val unpublishedProjects = setOf("benchmark", "guide", "kotlinx-serialization-json-tests")
val excludedFromBomProjects = unpublishedProjects + "kotlinx-serialization-bom"

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile<*>>().configureEach {
        if (name.contains("Test") || name.contains("Jmh")) {
            compilerOptions.freeCompilerArgs.addAll(experimentalsInTestEnabled)
        } else {
            compilerOptions.freeCompilerArgs.addAll(experimentalsEnabled)
        }
    }

    apply(plugin = "teamcity-conventions")
    // Configure publishing for some artifacts
    if (!unpublishedProjects.contains(project.name)) {
        apply(plugin = "publishing-conventions")
    }
}

subprojects {
    // Can't be applied to BOM
    if (excludedFromBomProjects.contains(project.name)) return@subprojects

    // Animalsniffer setup
    // Animalsniffer requires java plugin to be applied, but Kotlin 1.9.20
    // relies on `java-base` for Kotlin Multiplatforms `withJava` implementation
    // https://github.com/xvik/gradle-animalsniffer-plugin/issues/84
    // https://youtrack.jetbrains.com/issue/KT-59595
    apply(plugin = "java-conventions")
    apply(plugin = "ru.vyarus.animalsniffer")

    afterEvaluate { // Can be applied only when the project is evaluated
        extensions.configure<AnimalSnifferExtension> {
            sourceSets = listOf(this@subprojects.sourceSets["main"])

            val annotationValue = when(name) {
                "kotlinx-serialization-core" -> "kotlinx.serialization.internal.SuppressAnimalSniffer"
                "kotlinx-serialization-hocon" -> "kotlinx.serialization.hocon.internal.SuppressAnimalSniffer"
                "kotlinx-serialization-protobuf" -> "kotlinx.serialization.protobuf.internal.SuppressAnimalSniffer"
                else -> "kotlinx.serialization.json.internal.SuppressAnimalSniffer"
            }

            annotation = annotationValue
        }
        dependencies {
            "signature"("net.sf.androidscents.signature:android-api-level-14:4.0_r4@signature")
            "signature"("org.codehaus.mojo.signature:java18:1.0@signature")
        }

        // Add dependency on kotlinx-serialization-bom inside other kotlinx-serialization modules themselves, so they have same versions
        apply(plugin = "bom-conventions")
    }
}

// Kover setup
val uncoveredProjects = setOf("kotlinx-serialization-bom", "benchmark", "guide", "kotlinx-serialization-json-okio")

subprojects {
    if (uncoveredProjects.contains(project.name)) return@subprojects

    apply(plugin = "kover-conventions")
}

// Dokka setup
apply(plugin = "org.jetbrains.dokka")

val documentedSubprojects = setOf("kotlinx-serialization-core",
    "kotlinx-serialization-json",
    "kotlinx-serialization-json-okio",
    "kotlinx-serialization-cbor",
    "kotlinx-serialization-properties",
    "kotlinx-serialization-hocon",
    "kotlinx-serialization-protobuf")

subprojects {
    if (name in documentedSubprojects) {
        apply(plugin = "dokka-conventions")
    }
}

// Knit relies on Dokka task and it's pretty convenient
tasks.register("dokka") {
    dependsOn("dokkaHtmlMultiModule")
}

tasks.withType<DokkaMultiModuleTask>().named("dokkaHtmlMultiModule") {
    pluginsMapConfiguration.put("org.jetbrains.dokka.base.DokkaBase", """{ "templatesDir": "${projectDir.toString().replace("\\", "/")}/dokka-templates" }""")
}

dependencies {
    "dokkaPlugin"("org.jetbrains.kotlinx:dokka-pathsaver-plugin:${property("knit_version")}")
}

/// -===

apply(plugin = "compiler-version-conventions")
apply(plugin = "benchmark-conventions")

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask>().configureEach {
    args.add("--ignore-engines")
}

val Project.sourceSets: SourceSetContainer get() =
    extensions.getByName("sourceSets") as SourceSetContainer
