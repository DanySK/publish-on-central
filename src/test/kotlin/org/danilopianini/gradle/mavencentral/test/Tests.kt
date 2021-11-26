package org.danilopianini.gradle.mavencentral.test

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.file.shouldBeAFile
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class Tests : StringSpec({
    fun folder(closure: TemporaryFolder.() -> Unit) = TemporaryFolder().apply {
        create()
        closure()
    }
    fun TemporaryFolder.file(name: String, content: () -> String) = newFile(name).writeText(content().trimIndent())
    val workingDirectory = folder {
        file("settings.gradle") { "rootProject.name = 'testproject'" }
        file("build.gradle.kts") {
            """
                plugins {
                    `java-library`
                    `java-gradle-plugin`
                    id("org.danilopianini.publish-on-central")
                }
                publishOnCentral {
                    projectDescription = "test"
                    repository("https://maven.pkg.github.com/OWNER/REPOSITORY") {
                        name = "github"
                        user = "test"
                    }
                }
            """.trimIndent()
        }
    }
    val pluginClasspathResource = ClassLoader.getSystemClassLoader()
        .getResource("plugin-classpath.txt")
        ?: throw IllegalStateException("Did not find plugin classpath resource, run \"testClasses\" build task.")
    val classpath = pluginClasspathResource.openStream().bufferedReader().use { reader ->
        reader.readLines().map { File(it) }
    }
    val taskName = "generatePomFileForJavaMavenPublication"
    "correct configuration should work" {
        val result = GradleRunner.create()
            .withProjectDir(workingDirectory.root)
            .withPluginClasspath(classpath)
            .withArguments(taskName, "sourcesJar", "javadocJar", "--stacktrace")
            .build()
        println(result.tasks)
        println(result.output)
        val deps = result.task(":$taskName")
        deps?.outcome shouldBe TaskOutcome.SUCCESS
        with(File("${workingDirectory.root}/build/publications/javaMaven/pom-default.xml")) {
            shouldExist()
            shouldBeAFile()
            val contents = readText(Charsets.UTF_8)
            println(contents)
            contents shouldContain "artifactId"
            contents shouldContain "groupId"
            contents shouldContain "name"
            contents shouldContain "description"
            contents shouldContain "url"
            contents shouldContain "license"
            contents shouldContain "scm"
        }
        val tasks = GradleRunner.create()
            .withProjectDir(workingDirectory.root)
            .withPluginClasspath(classpath)
            .withArguments("tasks")
            .build()
        println(tasks.output)
        tasks.output shouldContain "publishJavaMavenPublication"
        tasks.output shouldContain "publishPluginMavenPublicationToGithubRepository"
        tasks.output shouldContain "publishPluginMavenPublicationToMavenCentralRepository"
        tasks.output shouldContain "releaseJavaMavenOnMavenCentralNexus"
        tasks.output shouldNotContain "releaseJavaMavenOnGitHubNexus"
    }
})
