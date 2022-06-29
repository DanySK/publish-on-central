package org.danilopianini.gradle.mavencentral

import org.danilopianini.gradle.mavencentral.ProjectExtensions.configureExtension
import org.danilopianini.gradle.mavencentral.ProjectExtensions.createExtension
import org.danilopianini.gradle.mavencentral.ProjectExtensions.registerTaskIfNeeded
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

/**
 * A Plugin configuring the project for publishing on Maven Central.
 */
class PublishOnCentral : Plugin<Project> {
    companion object {
        /**
         * The name of the publication to be created.
         */
        private const val publicationName = "OSSRH"
    }

    override fun apply(project: Project) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)
        val extension = project.createExtension<PublishOnCentralExtension>("publishOnCentral", project)
        val createdPublications = mutableListOf<MavenPublication>()
        project.configureExtension<PublishingExtension> {
            val sourcesJarTask = project.registerTaskIfNeeded<SourceJar>("sourcesJar")
            val javadocJarTask = project.registerTaskIfNeeded<JavadocJar>("javadocJar")
            project.tasks.matching { it.name == "assemble" }.configureEach {
                it.dependsOn(sourcesJarTask, javadocJarTask)
            }
            project.components.configureEach { component ->
                publications { publications ->
                    val name = "${component.name}$publicationName"
                    if (publications.none { it.name == name }) {
                        publications.create(name, MavenPublication::class.java) { publication ->
                            createdPublications += publication
                            publication.from(component)
                            publication.artifact(sourcesJarTask)
                            publication.artifact(javadocJarTask)
                            publication.configurePomForMavenCentral(extension)
                            publication.pom.packaging = "jar"
                            project.configure<SigningExtension> {
                                sign(publication)
                            }
                        }
                        project.logger.debug("Created new publication $name")
                    }
                }
            }
            publications.withType<MavenPublication>().configureEach { publication ->
                if (extension.autoConfigureAllPublications.getOrElse(true) && publication !in createdPublications) {
                    publication.configurePomForMavenCentral(extension)
                    if (publication.findSigningTaskIn(project).isEmpty()) {
                        project.configure<SigningExtension> {
                            sign(publication)
                        }
                    }
                }
            }
        }
        project.afterEvaluate {
            if (extension.configureMavenCentral.getOrElse(true)) {
                project.configureRepository(extension.mavenCentral)
            }
        }
        project.plugins.withType<JavaPlugin>().configureEach { _ ->
            project.tasks.withType<JavadocJar>().configureEach { javadocJar ->
                val javadocTask = project.tasks.findByName("javadoc") as? Javadoc
                    ?: throw IllegalStateException("Java plugin applied but no Javadoc task existing!")
                javadocJar.dependsOn(javadocTask)
                javadocJar.from(javadocTask.destinationDir)
            }
            project.tasks.withType(SourceJar::class.java).configureEach { it.sourceSet("main", true) }
        }
        val dokkaPluginClass = runCatching { Class.forName("org.jetbrains.dokka.gradle.DokkaPlugin") }
        if (dokkaPluginClass.isSuccess) {
            @Suppress("UNCHECKED_CAST")
            project.plugins.withType(dokkaPluginClass.getOrThrow() as Class<Plugin<*>>).configureEach {
                project.tasks.withType(JavadocJar::class.java).configureEach { javadocJar ->
                    val dokkaJavadoc = project.tasks.findByName("dokkaJavadoc")
                        ?: throw IllegalStateException("Dokka plugin applied but no dokkaJavadoc task existing!")
                    val outputDirectory = dokkaJavadoc.property("outputDirectory")
                        ?: throw IllegalStateException(
                            "dokkaJavadoc has no property 'outputDirectory' - " +
                                "maybe this version is incompatible with publish-on-central?"
                        )
                    javadocJar.dependsOn(dokkaJavadoc)
                    javadocJar.from(outputDirectory)
                }
            }
        }
    }
}
