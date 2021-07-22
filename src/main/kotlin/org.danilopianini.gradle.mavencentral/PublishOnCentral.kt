package org.danilopianini.gradle.mavencentral

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import java.io.File

/**
 * A Plugin configuring the project for publishing on Maven Central.
 */
class PublishOnCentral : Plugin<Project> {
    companion object {
        /**
         * The name of the publication to be created.
         */
        const val publicationName = "maven"
        private inline fun <reified T> Project.extension(): T = project.extensions.getByType(T::class.java)
        private inline fun <reified T> Project.createExtension(name: String, vararg args: Any?): T =
            project.extensions.create(name, T::class.java, *args)

        private inline fun <reified T> Project.configure(crossinline body: T.() -> Unit): Unit =
            project.extensions.configure(T::class.java) { it.body() }
    }

    inline fun <reified T : Task> Project.registerTaskIfNeeded(name: String): Task =
        tasks.findByName(name) ?: project.tasks.register(name, T::class.java).get()

    override fun apply(project: Project) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)
        val extension = project.createExtension<PublishOnCentralExtension>("publishOnCentral", project)
        project.configure<PublishingExtension> {
            val sourcesJarTask = project.registerTaskIfNeeded<SourcesJar>("sourcesJar")
            val javadocJarTask = project.registerTaskIfNeeded<JavadocJar>("javadocJar")
            fun createPublications(component: SoftwareComponent) {
                project.logger.debug("Reacting to the creation of component ${component.name}")
                publications { publications ->
                    val name = "${component.name}${publicationName.capitalize()}"
                    if (publications.none { it.name == name }) {
                        val publication = publications.create(name, MavenPublication::class.java) { publication ->
                            publication.from(component)
                        }
                        project.logger.debug("Created new publication $name")
                        publication.artifact(sourcesJarTask)
                        publication.artifact(javadocJarTask)
                        with (extension) {
                            publication.configurePomForMavenCentral()
                        }
                        // Signing
                        project.configure<SigningExtension> {
                            sign(publication)
                        }
                    }
                }
            }
            project.components.forEach(::createPublications)
            project.components.whenObjectAdded(::createPublications)
        }
        project.afterEvaluate {
            Repository.mavenCentral.configureForProject(project)
        }
        project.plugins.withType(JavaPlugin::class.java) { _ ->
            project.tasks.withType(JavadocJar::class.java) { javadocJar ->
                val javadocTask = project.tasks.findByName("javadoc") as? Javadoc
                    ?: throw IllegalStateException("Java plugin applied but no Javadoc task existing!")
                javadocJar.dependsOn(javadocTask)
                javadocJar.from(javadocTask.destinationDir)
            }
            project.tasks.withType(SourcesJar::class.java) { it.sourceSet("main", true) }
        }
        val dokkaPluginClass = kotlin.runCatching { Class.forName("org.jetbrains.dokka.gradle.DokkaPlugin") }
        if (dokkaPluginClass.isSuccess) {
            @Suppress("UNCHECKED_CAST")
            project.plugins.withType(dokkaPluginClass.getOrThrow() as Class<Plugin<*>>) {
                project.tasks.withType(JavadocJar::class.java) { javadocJar ->
                    val dokkaJavadoc = project.tasks.findByName("dokkaJavadoc")
                        ?: throw IllegalStateException("Dokka plugin applied but no dokkaJavadoc task existing!")
                    val outputDirectory = dokkaJavadoc.property("outputDirectory")
                        ?: throw IllegalStateException("dokkaJavadoc has no property 'outputDirectory' - " +
                            "maybe this version is incompatible with publish-on-central?")
                    javadocJar.dependsOn(dokkaJavadoc)
                    javadocJar.from(outputDirectory)
                }
            }
        }
    }
}

/**
 * A [Jar] task with the specified classifier, and adopting the duplicate strategy
 * [org.gradle.api.file.DuplicatesStrategy.WARN].
 */
open class JarWithClassifier(classifier: String) : Jar() {
    init {
        archiveClassifier.set(classifier)
        duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.WARN
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
        if (sourceSet != null) {
            sourceSet(sourceSet)
        } else if (failOnMissingName) {
            throw IllegalStateException("Project has no source set named $name")
        }
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

open class JavadocJar: JarWithClassifier("javadoc")
