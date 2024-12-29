@file:Suppress("UnstableApiUsage")

import org.gradle.kotlin.dsl.repositories

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("multiplatform")
    id("org.danilopianini.publish-on-central")
}

group = "org.danilopianini"
version = "1.0.0"

repositories {
    mavenCentral()
}

publishOnCentral {
    repoOwner.set("DanySK")
    projectDescription.set("test")
}

kotlin {
    js {
        browser()
        nodejs()
        binaries.library()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<Sign>().configureEach {
    enabled = false
}
