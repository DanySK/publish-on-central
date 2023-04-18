@file:Suppress("UnstableApiUsage")

import org.danilopianini.gradle.mavencentral.DocStyle

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("js")
    id("org.danilopianini.publish-on-central")
}

group = "org.danilopianini"
version = "1.0.0"

kotlin {
    js {
        browser()
        nodejs()
        binaries.library()
    }
    sourceSets {
        val main by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val test by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
