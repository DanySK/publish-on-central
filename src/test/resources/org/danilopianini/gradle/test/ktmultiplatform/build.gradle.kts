@file:Suppress("UnstableApiUsage")

import org.danilopianini.gradle.mavencentral.DocStyle
import org.danilopianini.gradle.mavencentral.JavadocJar
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("multiplatform")
    id("org.danilopianini.publish-on-central")
    id("org.jetbrains.dokka") version "1.8.10"
}

group = "org.danilopianini"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser()
        nodejs()
        binaries.library()
    }
    val hostOs = System.getProperty("os.name").trim().toLowerCaseAsciiOnly()
    val hostArch = System.getProperty("os.arch").trim().toLowerCaseAsciiOnly()
    val nativeTarget: (String, org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.() -> Unit) -> KotlinTarget =
        when (hostOs to hostArch) {
            "linux" to "aarch64" -> ::linuxArm64
            "linux" to "amd64" -> ::linuxX64
            "linux" to "arm", "linux" to "arm32" -> ::linuxArm32Hfp
            "linux" to "mips", "linux" to "mips32" -> ::linuxMips32
            "linux" to "mipsel", "linux" to "mips32el" -> ::linuxMipsel32
            "mac os x" to "aarch64" -> ::macosArm64
            "mac os x" to "amd64", "mac os x" to "x86_64" -> ::macosX64
            "windows" to "amd64", "windows server 2022" to "amd64" -> ::mingwX64
            "windows" to "x86" -> ::mingwX86
            else -> throw GradleException("Host OS '$hostOs' with arch '$hostArch' is not supported in Kotlin/Native.")
        }
    nativeTarget("native") {
        binaries {
            sharedLib()
            staticLib()
            // Remove if it is not executable
            "main".let { executable ->
                executable {
                    entryPoint = executable
                }
                // Enable wasm32
                wasm32 {
                    binaries {
                        executable {
                            entryPoint = executable
                        }
                    }
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val nativeMain by getting {
            dependsOn(commonMain)
        }
        val nativeTest by getting {
            dependsOn(commonTest)
        }
    }
    targets.all {
        compilations.all {
            kotlinOptions {
                allWarningsAsErrors = true
            }
        }
    }
}

signing {
    if (System.getenv("CI") == "true") {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
}

publishOnCentral {
    docStyle.set(DocStyle.HTML)
    projectLongName.set("Template for Kotlin Multiplatform Project")
    projectDescription.set("A template repository for Kotlin Multiplatform projects")
    repository("https://maven.pkg.github.com/danysk/${rootProject.name}".toLowerCase()) {
        user.set("DanySK")
        password.set(System.getenv("GITHUB_TOKEN"))
    }
    publishing {
        publications {
            withType<MavenPublication> {
                pom {
                    developers {
                        developer {
                            name.set("Danilo Pianini")
                            email.set("danilo.pianini@gmail.com")
                            url.set("http://www.danilopianini.org/")
                        }
                    }
                }
            }
        }
    }
}
