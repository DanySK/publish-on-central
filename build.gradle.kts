import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.palantir.gradle.gitversion.*
import groovy.lang.Closure
import org.gradle.jvm.tasks.Jar
import java.net.URI

plugins {
    `java-gradle-plugin`
    `java`
    `maven-publish`
    `signing`
    id("com.palantir.git-version") version "0.12.0-rc2"
    kotlin("jvm") version "1.3.21"
    id("com.gradle.plugin-publish") version "0.10.1"
    id ("org.danilopianini.publish-on-central") version "0.1.0-dev01+4274224e"
    id("org.jetbrains.dokka") version "0.9.17"
}

group = "org.danilopianini"
val projectId = "$group.$name"
val fullName = "Gradle Publish On Maven Central Plugin"
val websiteUrl = "https://github.com/DanySK/maven-central-gradle-plugin"
val projectDetails = "A Plugin for easily publishing artifacts on Maven Central"
val pluginImplementationClass = "org.danilopianini.gradle.mavencentral.PublishOnCentral"

val versionDetails: VersionDetails = (property("versionDetails") as? Closure<VersionDetails>)?.call()
    ?: throw IllegalStateException("Unable to fetch the git version for this repository")
fun Int.asBase(base: Int = 36, digits: Int = 2) = toString(base).let {
    if (it.length >= digits) it
    else generateSequence {"0"}.take(digits - it.length).joinToString("") + it
}
val minVer = "0.1.0"
val semVer = """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-(0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(\.(0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*)?(\+[0-9a-zA-Z-]+(\.[0-9a-zA-Z-]+)*)?${'$'}""".toRegex()
version = with(versionDetails) {
    val tag = lastTag ?.takeIf { it.matches(semVer) }
    val baseVersion = tag ?: minVer
    val appendix = tag?.let {
        "".takeIf { commitDistance == 0 } ?: "-dev${commitDistance.asBase()}+${gitHash}"
    } ?: "-archeo+${gitHash}"
    baseVersion + appendix
}.take(20)
if (!version.toString().matches(semVer)) {
    throw IllegalStateException("Version ${version} does not match Semantic Versioning requirements")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation("io.kotlintest:kotlintest-runner-junit5:+")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_6
}

publishOnCentral {
    projectDescription.set(projectDetails)
    projectLongName.set(fullName)
    projectUrl.set(websiteUrl)
    scmConnection.set("git:git@github.com:DanySK/maven-central-gradle-plugin")
}

tasks {
    "test"(Test::class) {
        useJUnitPlatform()
        testLogging.showStandardStreams = true
        testLogging {
            showCauses = true
            showStackTraces = true
            showStandardStreams = true
            events(*TestLogEvent.values())
        }
    }
    register("createClasspathManifest") {
        val outputDir = file("$buildDir/$name")
        inputs.files(sourceSets.main.get().runtimeClasspath)
        outputs.dir(outputDir)
        doLast {
            outputDir.mkdirs()
            file("$outputDir/plugin-classpath.txt").writeText(sourceSets.main.get().runtimeClasspath.joinToString("\n"))
        }
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.6"
    }
}

// Add the classpath file to the test runtime classpath
dependencies {
    testRuntimeOnly(files(tasks["createClasspathManifest"]))
}

publishing {
    publications {
        withType<MavenPublication>() {
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

pluginBundle {
    website = websiteUrl
    vcsUrl = websiteUrl
    tags = listOf("maven", "maven central", "ossrh", "central", "publish")
}

gradlePlugin {
    plugins {
        create(projectId) {
            id = projectId
            displayName = fullName
            description = projectDetails
            implementationClass = pluginImplementationClass
        }
    }
}

