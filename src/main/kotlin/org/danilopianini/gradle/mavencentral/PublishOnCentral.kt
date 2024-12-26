package org.danilopianini.gradle.mavencentral

import org.danilopianini.gradle.mavencentral.MavenPublicationExtensions.configurePomForMavenCentral
import org.danilopianini.gradle.mavencentral.MavenPublicationExtensions.signingTasks
import org.danilopianini.gradle.mavencentral.ProjectExtensions.configureRepository
import org.danilopianini.gradle.mavencentral.ProjectExtensions.registerTaskIfNeeded
import org.danilopianini.gradle.mavencentral.ProjectExtensions.setupMavenCentralPortal
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

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
    }

    override fun apply(project: Project) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)
        val extension = project.extensions.create<PublishOnCentralExtension>("publishOnCentral", project)
        val createdPublications = mutableListOf<MavenPublication>()
        project.configure<PublishingExtension> {
            val sourcesJarTask = project.registerTaskIfNeeded("sourcesJar", SourceJar::class)
            val javadocJarTask = project.registerTaskIfNeeded("javadocJar", JavadocJar::class)
            project.tasks.matching { it.name == "assemble" }.configureEach {
                it.dependsOn(sourcesJarTask, javadocJarTask)
            }
            project.components.configureEach { component ->
                publications { publications ->
                    val name = "${component.name}$PUBLICATION_NAME"
                    if (publications.none { it.name == name }) {
                        publications.register(name, MavenPublication::class.java) { publication ->
                            createdPublications += publication
                            publication.from(component)
                            publication.artifact(sourcesJarTask)
                            publication.artifact(javadocJarTask)
                            publication.pom.packaging = "jar"
                            project.configure<SigningExtension> {
                                sign(publication)
                            }
                        }
                        project.logger.debug("Created new publication $name")
                    }
                }
            }
            publications
                .withType<MavenPublication>()
                .configureEach { publication ->
                    if (extension.autoConfigureAllPublications.getOrElse(true) || publication in createdPublications) {
                        project.logger.info(
                            "Populating data of publication {} in {}, group {}",
                            publication.name,
                            project,
                            project.group,
                        )
                        publication.configurePomForMavenCentral(extension)
                        if (publication.signingTasks(project).isEmpty()) {
                            project.configure<SigningExtension> {
                                sign(publication)
                            }
                        }
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
        project.pluginManager.withPlugin("java") { _ ->
            project.tasks.withType<JavadocJar>().configureEach { javadocJar ->
                val javadocTask =
                    checkNotNull(project.tasks.findByName("javadoc") as? Javadoc) {
                        "Java plugin applied but no Javadoc task existing!"
                    }
                javadocJar.dependsOn(javadocTask)
                javadocJar.from(javadocTask.destinationDir)
            }
            project.tasks.withType(SourceJar::class.java).configureEach { it.sourceSet("main", true) }
        }
        // React to Dokka application
        project.pluginManager.withPlugin(DOKKA_PLUGIN_ID) { _ ->
            project.logger.info("Dokka plugin found, hence javadocJar will be configured")
            project.tasks.withType(JavadocJar::class.java).configureEach { javadocJar ->
                val dokkaTask =
                    extension.docStyle.map { docStyle ->
                        project
                            .dokkaTasksFor(docStyle)
                            .firstOrNull()
                            ?: error("Dokka plugin applied but no task exists for style $docStyle!")
                    }
                javadocJar.dependsOn(dokkaTask)
                javadocJar.from(dokkaTask)
            }
        }
    }
}
