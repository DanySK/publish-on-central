package org.danilopianini.gradle.mavencentral

import org.danilopianini.gradle.mavencentral.MavenConfigurationSupport.configurePomForMavenCentral
import org.danilopianini.gradle.mavencentral.MavenConfigurationSupport.configureRepository
import org.danilopianini.gradle.mavencentral.ProjectExtensions.registerTaskIfNeeded
import org.danilopianini.gradle.mavencentral.ProjectExtensions.setupMavenCentralPortal
import org.danilopianini.gradle.mavencentral.tasks.JavadocJar
import org.danilopianini.gradle.mavencentral.tasks.SourceJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.tasks.DokkaGenerateTask

/**
 * A Plugin configuring the project for publishing on Maven Central.
 */
class PublishOnCentral : Plugin<Project> {
    /**
     * Constants.
     */
    companion object {
        /**
         * The name of the publication to be created.
         */
        private const val PUBLICATION_NAME = "OSSRH"

        private fun Project.javadocJarTask() = project.registerTaskIfNeeded("javadocJar", JavadocJar::class)

        private fun Project.sourcesJarTask() = project.registerTaskIfNeeded("sourcesJar", SourceJar::class)
    }

    override fun apply(project: Project) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)
        val extension = project.extensions.create<PublishOnCentralExtension>("publishOnCentral", project)
        project.pluginManager.withPlugin("java") { _ ->
            project.configure<JavaPluginExtension> {
                withJavadocJar()
                withSourcesJar()
            }
            project.configure<PublishingExtension> {
                publications { publications ->
                    val name = PUBLICATION_NAME
                    if (publications.none { it.name == name }) {
                        publications.register(name, MavenPublication::class.java) { publication ->
                            publication.artifact(project.tasks.withType<Jar>().named("jar"))
                            val javadocJarTask = project.javadocJarTask()
                            javadocJarTask.configure {
                                if (it is JavadocJar) {
                                    it.from(project.tasks.withType<Javadoc>())
                                }
                            }
                            publication.artifact(javadocJarTask)
                            javadocJarTask.configure {
                                if (it is CopySpec) {
                                    it.duplicatesStrategy = DuplicatesStrategy.INCLUDE
                                }
                            }
                            val sourcesJar = project.sourcesJarTask()
                            publication.artifact(sourcesJar)
                            publication.pom.packaging = "jar"
                        }
                        project.logger.debug("Created new publication $name")
                    }
                }
            }
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") { _ ->
            project.configure<PublishingExtension> {
                publications.withType<MavenPublication>().all { publication ->
                    publication.artifact(project.javadocJarTask())
                }
            }
        }
        project.configure<PublishingExtension> {
            publications
                .withType<MavenPublication>()
                .all { publication ->
                    publication.configurePomForMavenCentral(extension)
                    project.configure<SigningExtension> {
                        sign(publication)
                    }
                }
        }
        project.tasks.withType<PublishToMavenRepository>().configureEach { publish ->
            publish.mustRunAfter(project.tasks.withType<Sign>())
        }
        // Maven Central Portal
        project.setupMavenCentralPortal()
        // Initialize Central
        project.configureRepository(extension.mavenCentral)
        // React to Dokka application
        project.pluginManager.withPlugin("org.jetbrains.dokka") { _ ->
            project.logger.info("Dokka plugin found, hence javadocJar will be configured")
            project.tasks.withType<Javadoc>().configureEach { it.enabled = false }
            project.tasks.withType<Jar>().matching { "javadoc" in it.name }.configureEach { javadocJar ->
                javadocJar.from(project.tasks.withType<DokkaGenerateTask>())
                javadocJar.from(
                    project.tasks.withType<DokkaTask>().matching { it.name.contains("html", ignoreCase = true) },
                )
            }
        }
    }
}
