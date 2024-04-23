import Java9Modularity.configureJava9ModuleInfo

/*
 * Copyright 2017-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
}

apply(from = rootProject.file("gradle/native-targets.gradle"))
apply(from = rootProject.file("gradle/configure-source-sets.gradle"))


kotlin {

    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlinx-serialization-core"))
            }
        }

        val jackson_version = property("jackson_version") as String

        jvmTest {
            dependencies {
                implementation("io.kotlintest:kotlintest:2.0.7")
                implementation("com.upokecenter:cbor:4.0.0-beta1")
                implementation("com.fasterxml.jackson.core:jackson-core:$jackson_version")
                implementation("com.fasterxml.jackson.core:jackson-databind:$jackson_version")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
                implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:$jackson_version")
            }
        }
    }
}

configureJava9ModuleInfo()
