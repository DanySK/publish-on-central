package org.danilopianini.gradle.mavencentral

import org.danilopianini.gradle.mavencentral.MavenPublicationExtensions.configurePomForMavenCentral
import org.danilopianini.gradle.mavencentral.MavenPublicationExtensions.signingTasks
import org.danilopianini.gradle.mavencentral.ProjectExtensions.addSourcesArtifactIfNeeded
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
import org.gradle.jvm.tasks.Jar
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

        /**
         * The id of the Kotlin/JS plugin.
         */
        internal const val KOTLIN_JS_PLUGIN = "org.jetbrains.kotlin.js"
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
                            project.addSourcesArtifactIfNeeded(publication, sourcesJarTask)
                            if (javadocJarTask is JavadocJar) {
                                publication.artifact(javadocJarTask)
                            }
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
        /*
         * This is a hack for Kotlin/JS projects.
         * These projects already contain tasks named "jsSourcesJar" and "kotlinSourcesJar", generating the
         * same jar "<project.name>-js-<project.version>-sources.jar".
         * In particular,
         * task kotlinSourcesJar is automatically registered as an artifact to Maven publications
         * when they are created. So, adding further sources-jar-generating tasks can be troublesome.
         * The following code simply removes the "-js" appendix from the jar file name,
         * hence making the jar compliant with Maven Central.
         */
        project.pluginManager.withPlugin(KOTLIN_JS_PLUGIN) { _ ->
            project.tasks
                .withType<Jar>()
                .named { name ->
                    name in listOf("js", "kotlin").map { "${it}SourcesJar" }
                }.configureEach { kotlinJsJar ->
                    /*
                     * TODO: Kotlin seems to override the archiveAppendix property later in the configuration.
                     * We thus force it in its `doFirst` block.
                     * This behavior should get reverted as soon as possible,
                     * when the Kotlin plugin will be fixed.
                     */
                    kotlinJsJar.archiveAppendix.set("")
                    kotlinJsJar.doFirst {
                        kotlinJsJar.archiveAppendix.set("")
                        project.logger.lifecycle(
                            """
                            publish-on-central is working around the `archiveAppendix` behavior of the Kotlin/JS plugin.
                            if you need task to have a different appendix, please reconfigure it
                            inside the `doFirst`.
                            """.trimIndent().replace(Regex("\\R"), " "),
                            kotlinJsJar.name,
                        )
                    }
                    // Better tell the user the plugin is changing the behaviour of default tasks
                    project.logger.lifecycle(
                        "Removing the '-js' appendix from sources jar generated by task {}",
                        kotlinJsJar.name,
                    )
                }
        }
        // React to Dokka application
        project.pluginManager.withPlugin(DOKKA_PLUGIN_ID) { _ ->
            project.logger.info("Dokka plugin found, hence javadocJar will be configured")
            project.tasks.withType(JavadocJar::class.java).configureEach { javadocJar ->
                val message = "configure ${javadocJar.name} task to depend on Dokka task"
                project.logger.info("Lazily $message")
                val dokkaTask =
                    extension.docStyle.map { docStyle ->
                        project
                            .dokkaTasksFor(docStyle)
                            .firstOrNull()
                            ?.also { project.logger.info("Actually {} {}", message, it.name) }
                            ?: error("Dokka plugin applied but no task exists for style $docStyle!")
                    }
                javadocJar.dependsOn(dokkaTask)
                javadocJar.from(dokkaTask)
            }
        }
    }
}
