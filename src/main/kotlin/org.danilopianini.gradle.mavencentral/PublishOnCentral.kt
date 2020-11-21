package org.danilopianini.gradle.mavencentral

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.PluginCollection
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

/**
 * A Plugin configuring the project for publishing on Maven Central
 */
class PublishOnCentral : Plugin<Project> {
    companion object {
        /**
         * The name of the publication to be created.
         */
        const val publicationName = "mavenCentral"
        val logger = LoggerFactory.getLogger("publish-on-central plugin")
        private inline fun <reified T> Project.extension(): T = project.extensions.getByType(T::class.java)
        private inline fun <reified T> Project.createExtension(name: String, vararg args: Any?): T =
            project.extensions.create(name, T::class.java, *args)

        private inline fun <reified S, reified T : Plugin<S>> Project.plugin(): PluginCollection<T> =
            project.plugins.withType(T::class.java)

        private inline fun <reified T> Project.configure(crossinline body: T.() -> Unit): Unit =
            project.extensions.configure(T::class.java) { it.body() }

        private inline fun <reified T : Task> Project.configureTask(crossinline body: T.() -> Unit) =
            project.tasks.withType(T::class.java) { it.body() }
    }

    inline fun <reified T : Task> Project.registerTaskIfNeeded(name: String) =
        tasks.findByName(name) ?: project.tasks.register(name, T::class.java)

    override fun apply(project: Project) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)
        val extension = project.createExtension<PublishOnCentralExtension>("publishOnCentral", project)
        project.configure<PublishingExtension> {
            project.registerTaskIfNeeded<SourcesJar>("sourcesJar")
            project.registerTaskIfNeeded<JavadocJar>("javadocJar")
            // Create the publication
            publications { publications ->
                val publication = publications.create(publicationName, MavenPublication::class.java) { publication ->
                    project.components.forEach {
                        publication.from(it)
                    }
                    publication.artifact(project.property("sourcesJar"))
                    publication.artifact(project.property("javadocJar"))
                    with (extension) {
                        publication.configurePomForMavenCentral()
                    }
                }
                // Signing
                project.configure<SigningExtension> {
                    sign(publication)
                }
            }
            // Add all destinations
            extension.configuration.repositories.forEach { (repoName, repoDescriptor) ->
                repositories { repository ->
                    repository.maven { mavenArtifactRepository ->
                        mavenArtifactRepository.name = repoName
                        mavenArtifactRepository.url = URI(repoDescriptor.url.get())
                        mavenArtifactRepository.credentials { credentials ->
                            credentials.username = repoDescriptor.user.orNull
                            credentials.password = repoDescriptor.password.orNull
                            credentials.username ?: logger.warn("No username configured for ${repoDescriptor}.")
                            credentials.password ?: logger.warn("No password configured for ${repoDescriptor}.")
                        }
                    }
                }
            }
        }
    }

}

open class JarWithClassifier(classifier: String) : Jar() {
    init {
        archiveClassifier.set(classifier)
    }
}

open class SourcesJar: JarWithClassifier("sources") {
    init {
        sourceSet("main", false)
    }

    @JvmOverloads
    fun sourceSet(name: String, failOnMissingName: Boolean = true) {
        val sourceSets = project.properties["sourceSets"] as? SourceSetContainer
        if (sourceSets == null && failOnMissingName) {
            throw IllegalStateException("Project has no property 'sourceSets' of type 'SourceSetContainer'")
        }
        val sourceSet = sourceSets?.getByName(name)
        if (sourceSet == null && failOnMissingName) {
            throw IllegalStateException("Project has no source set named $name")
        }
        from(sourceSet)
    }

    fun sourceSet(sourceSet: SourceSet) {
        sourceSet(sourceSet.allSource)
    }

    fun sourceSet(sourceSet: SourceDirectorySet) {
        from(sourceSet)
    }

    fun source(file: File) {
        from(file)
    }

}

open class JavadocJar: JarWithClassifier("javadoc") {
    init {
        (project.tasks.findByName("javadoc") as? Javadoc)?.also {
            dependsOn(it)
            from(it.destinationDir)
        }
        project.tasks.findByName("dokkaJavadoc")
            ?.also { dependsOn(it) }
            ?.property("outputDirectory")
            ?.also { from(it) }
    }
}