package org.danilopianini.gradle.mavencentral.test

import io.kotlintest.matchers.file.shouldBeAFile
import io.kotlintest.shouldBe
import io.kotlintest.matchers.file.shouldExist
import io.kotlintest.specs.StringSpec
import org.gradle.internal.impldep.com.google.common.io.Files
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class CentralTests : StringSpec({
    fun folder(closure: TemporaryFolder.() -> Unit) = TemporaryFolder().apply {
        create()
        closure()
    }
    fun TemporaryFolder.file(name: String, content: () -> String) = newFile(name).writeText(content().trimIndent())
    val workingDirectory = folder {
        file("settings.gradle") { "rootProject.name = 'testproject'" }
//        file("gradle.properties") { """
//        artifactId = testproject
//        projectDescription = A test project
//        scmRootUrl = someUrl
//        licenseName = myLicense
//        licenseUrl = anotherUrl
//        scmType = git
//        scmRepoName = myrepo
//        scmLogin = danysk
//        ossrhUsername = myOSSRHusername
//        ossrhPassword = This should be configured elsewhere
//    """ }
        file("build.gradle.kts") { """
        plugins {
//            id("java")
            id("java-library")
            id("maven-publish")
            id("signing")
            id("org.danilopianini.publish-on-central")
        }
    """ }
    }
    val pluginClasspathResource = ClassLoader.getSystemClassLoader()
        .getResource("plugin-classpath.txt")
        ?: throw IllegalStateException("Did not find plugin classpath resource, run \"testClasses\" build task.")
    val classpath = pluginClasspathResource.openStream().bufferedReader().use { reader ->
        reader.readLines().map { File(it) }
    }
    "correct configuration should work" {
        val result = GradleRunner.create()
            .withProjectDir(workingDirectory.root)
            .withPluginClasspath(classpath)
            .withArguments("-PossrhPassword=testpwd", "generatePomFileForMavenCentralPublication", "sourcesJar", "javadocJar")
            .build()
        println(result.tasks)
        println(result.output)
        val deps = result.task(":generatePomFileForMavenCentralPublication")
        deps?.outcome shouldBe TaskOutcome.SUCCESS
        with(File("${workingDirectory.root}/build/publications/mavenCentral/pom-default.xml")){
            shouldExist()
            shouldBeAFile()
        }
    }
})
