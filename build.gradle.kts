import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.palantir.gradle.gitversion.*
import groovy.lang.Closure
import org.gradle.jvm.tasks.Jar

plugins {
    id("java-gradle-plugin")
    id("java")
    id("com.palantir.git-version") version "0.12.0-rc2"
    kotlin("jvm") version "1.3.21"
    `maven-publish`
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])
        }
    }
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
}

// Add the classpath file to the test runtime classpath
dependencies {
    testRuntimeOnly(files(tasks["createClasspathManifest"]))
}
