import org.danilopianini.gradle.mavencentral.DocStyle
import org.danilopianini.gradle.mavencentral.JavadocJar
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.util.*

plugins {
    kotlin("multiplatform")
    id("org.danilopianini.publish-on-central")
    id("org.jetbrains.dokka")
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
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }
    }

    val nativeSetup: KotlinNativeTarget.() -> Unit = {
        compilations["main"].defaultSourceSet.dependsOn(kotlin.sourceSets["nativeMain"])
        compilations["test"].defaultSourceSet.dependsOn(kotlin.sourceSets["nativeTest"])
        binaries {
            executable()
            sharedLib()
            staticLib()
        }
    }

    applyDefaultHierarchyTemplate()
    /*
     * Linux 64
     */
    linuxX64(nativeSetup)
    linuxArm64(nativeSetup)
    /*
     * Win 64
     */
    mingwX64(nativeSetup)
    /*
     * Apple OSs
     */
    macosX64(nativeSetup)
    macosArm64(nativeSetup)
    iosArm64(nativeSetup)
    iosX64(nativeSetup)
    iosSimulatorArm64(nativeSetup)
    watchosArm32(nativeSetup)
    watchosX64(nativeSetup)
    watchosSimulatorArm64(nativeSetup)
    tvosArm64(nativeSetup)
    tvosX64(nativeSetup)
    tvosSimulatorArm64(nativeSetup)

    targets.all {
        compilations.all {
            kotlinOptions {
                allWarningsAsErrors = true
                freeCompilerArgs += listOf("-Xexpect-actual-classes")
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
    repository("https://maven.pkg.github.com/danysk/${rootProject.name}".lowercase()) {
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
