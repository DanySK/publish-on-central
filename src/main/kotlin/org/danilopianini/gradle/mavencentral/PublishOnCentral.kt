package org.danilopianini.gradle.mavencentral

import org.danilopianini.gradle.mavencentral.MavenPublicationExtensions.configurePomForMavenCentral
import org.danilopianini.gradle.mavencentral.MavenPublicationExtensions.signingTasks
import org.danilopianini.gradle.mavencentral.ProjectExtensions.configureJavadocJarTaskForKtJs
import org.danilopianini.gradle.mavencentral.ProjectExtensions.configureRepository
import org.danilopianini.gradle.mavencentral.ProjectExtensions.ifKotlinJsProject
import org.danilopianini.gradle.mavencentral.ProjectExtensions.jsSourcesJar
import org.danilopianini.gradle.mavencentral.ProjectExtensions.registerTaskIfNeeded
import org.danilopianini.gradle.mavencentral.ProjectExtensions.sourcesJarTasks
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
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

    private companion object {
        /**
         * The name of the publication to be created.
         */
        private const val publicationName = "OSSRH"
    }

    private fun addSourcesArtifactIfNeeded(project: Project, publication: MavenPublication, sourcesJarTask: Task) {
        if (sourcesJarTask is SourceJar) {
            if (project.jsSourcesJar == null) {
                publication.artifact(sourcesJarTask)
                project.logger.info(
                    "add sources jar artifact to publication {} from task {}",
                    publication.name,
                    sourcesJarTask.name,
                )
            } else {
                /*
                 * This is a hack for Kotlin/JS projects.
                 * These projects already contain tasks named "jsSourcesJar" and "kotlinSourcesJar", generating the
                 * same jar "<project.name>-js-<project.version>-sources.jar".
                 * In particular, task kotlinSourcesJar is automatically registered as an artifact to Maven publications
                 * when they are created. So, adding further sources-jar-generating tasks it troublesome in this
                 * situation. The following code simply removes the "-js" appendix from the jar file name,
                 * hence making the jar compliant with Maven Central.
                 */
                project.ifKotlinJsProject { _ ->
                    project.sourcesJarTasks.forEach {
                        it.archiveAppendix.set("")
                        // Better to tell the user the plugin is changing the behaviour of default tasks
                        project.logger.lifecycle(
                            "remove '-js' appendix from sources jar generated by task {}",
                            it.name,
                        )
                    }
                }
            }
        }
    }

    private fun addJavadocArtifactIfNeeded(project: Project, publication: MavenPublication, javadocJarTask: Task) {
        if (javadocJarTask is JavadocJar) {
            publication.artifact(javadocJarTask)
            project.logger.info(
                "add javadoc jar artifact to publication ${publication.name} from task ${javadocJarTask.name}",
            )
        }
    }

    override fun apply(project: Project) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)
        val extension = project.extensions.create<PublishOnCentralExtension>("publishOnCentral", project)
        val createdPublications = mutableListOf<MavenPublication>()
        project.configure<PublishingExtension> {
            val sourcesJarTask = project.registerTaskIfNeeded("sourcesJar", SourceJar::class)
            val javadocJarTask = project.registerTaskIfNeeded("javadocJar", JavadocJar::class)
            project.configureJavadocJarTaskForKtJs(sourcesJarTask)
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
                            addSourcesArtifactIfNeeded(project, publication, sourcesJarTask)
                            addJavadocArtifactIfNeeded(project, publication, javadocJarTask)
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
        project.afterEvaluate {
            if (extension.configureMavenCentral.getOrElse(true)) {
                project.configureRepository(extension.mavenCentral)
            }
        }
        project.plugins.withType<JavaPlugin>().configureEach { _ ->
            project.tasks.withType<JavadocJar>().configureEach { javadocJar ->
                val javadocTask = checkNotNull(project.tasks.findByName("javadoc") as? Javadoc) {
                    "Java plugin applied but no Javadoc task existing!"
                }
                javadocJar.dependsOn(javadocTask)
                javadocJar.from(javadocTask.destinationDir)
            }
            project.tasks.withType(SourceJar::class.java).configureEach { it.sourceSet("main", true) }
        }
        project.plugins.withId(DOKKA_PLUGIN_ID) { _ ->
            project.logger.info("Dokka plugin found, hence javadocJar will be configured")
            project.tasks.withType(JavadocJar::class.java).configureEach { javadocJar ->
                val message = "configure ${javadocJar.name} task to depend on Dokka task"
                project.logger.info("Lazily $message")
                val dokkaTask = extension.docStyle.map { docStyle ->
                    project.dokkaTasksFor(docStyle).firstOrNull()
                        ?.also { project.logger.info("Actually $message ${it.name}") }
                        ?: error("Dokka plugin applied but no task exists for style $docStyle!")
                }
                javadocJar.dependsOn(dokkaTask)
                javadocJar.from(dokkaTask.map { it.dokkaOutputDirectory })
            }
        }
    }
}
