import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.palantir.gradle.gitversion.*
import groovy.lang.Closure
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.backend.common.onlyIf
import java.net.URI

plugins {
    `java-gradle-plugin`
    `java`
    `maven-publish`
    `signing`
    id("com.palantir.git-version") version "0.12.0-rc2"
    kotlin("jvm") version "1.3.21"
}

group = "org.danilopianini"
val versionDetails: VersionDetails = (property("versionDetails") as? Closure<VersionDetails>)?.call()
    ?: throw IllegalStateException("Unable to fetch the git version for this repository")
fun Int.asBase(base: Int = 36, digits: Int = 3) = toString(base).let {
    if (it.length >= digits) it
    else generateSequence {"0"}.take(digits - it.length).joinToString("") + it
}
val minVer = "0.1.0"
val semVer = """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-(0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(\.(0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*)?(\+[0-9a-zA-Z-]+(\.[0-9a-zA-Z-]+)*)?${'$'}""".toRegex()
version = with(versionDetails) {
    val baseVersion = branchName?.let { lastTag }?.takeIf { it.matches(semVer) } ?: "0.1.0"
    val appendix = branchName?.let {
            if (isCleanTag) "" else "-dev${commitDistance.asBase()}+${gitHash}"
        } ?: "-archeo+${System.currentTimeMillis()}"
    baseVersion + appendix + (".dirty".takeIf { version.endsWith(it) } ?: "")
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
    register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        val sourceSets = project.properties["sourceSets"] as? SourceSetContainer
            ?: throw IllegalStateException("Unable to get sourceSets for project $project. Got ${project.properties["sourceSets"]}")
        val main = sourceSets.getByName("main").allSource
        from(main)
    }
    register<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")
        val javadoc = project.tasks.findByName("javadoc") as? Javadoc
            ?: throw IllegalStateException("Unable to get javadoc task for project $project. Got ${project.task("javadoc")}")
        from(javadoc.destinationDir)
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.6"
    }
    withType<Sign> {
        onlyIf { project.property("signArchivesIsEnabled")?.toString()?.toBoolean() ?: false }
    }
}

// Add the classpath file to the test runtime classpath
dependencies {
    testRuntimeOnly(files(tasks["createClasspathManifest"]))
}

publishing {
    publications {
        create<MavenPublication>("mavenCentral") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])
            artifact(project.property("sourcesJar"))
            artifact(project.property("javadocJar"))
            pom {
                name.set("Gradle Publish On Central Plugin")
                description.set("A Plugin for easily publishing artifacts on Maven Central")
                url.set("https://github.com/DanySK/maven-central-gradle-plugin")
                licenses {
                    license {
                        name.set("")
                        url.set("")
                    }
                }
                scm {
                    url.set(this@pom.url.get())
                    connection.set("git@github.com:DanySK/maven-central-gradle-plugin.git")
                    developerConnection.set(connection.get())
                }
            }
        }
    }
    repositories {
        maven {
            url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = "danysk"
                password = project.property("ossrhPassword").toString()
            }
        }
    }
}

configure<SigningExtension> {
    sign(publishing.publications.getByName("mavenCentral"))
}