package org.danilopianini.gradle.mavencentral

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningPlugin

/**
 * A Plugin configuring the project for publishing on Maven Central.
 */
class PublishOnCentral : Plugin<Project> {
    companion object {
        /**
         * The name of the publication to be created.
         */
        private const val publicationName = "Maven"
        private inline fun <reified T> Project.createExtension(name: String, vararg args: Any?): T =
            project.extensions.create(name, T::class.java, *args)

        private inline fun <reified T : Any> Project.configure(crossinline body: T.() -> Unit): Unit =
            project.extensions.configure(T::class.java) { it.body() }
    }

    private inline fun <reified T : Task> Project.registerTaskIfNeeded(name: String): Task =
        tasks.findByName(name) ?: project.tasks.register(name, T::class.java).get()

    override fun apply(project: Project) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(SigningPlugin::class.java)
        val extension = project.createExtension<PublishOnCentralExtension>("publishOnCentral", project)
        var createdPublications = emptySet<MavenPublication>()
        project.configure<PublishingExtension> {
            val sourcesJarTask = project.registerTaskIfNeeded<JarTasks>("sourcesJar")
            val javadocJarTask = project.registerTaskIfNeeded<JavadocJar>("javadocJar")
            project.tasks.findByName("assemble")?.dependsOn(sourcesJarTask, javadocJarTask)
            fun createPublications(component: SoftwareComponent) {
                project.logger.debug("Reacting to the creation of component ${component.name}")
                publications { publications ->
                    val name = "${component.name}$publicationName"
                    if (publications.none { it.name == name }) {
                        publications.create(name, MavenPublication::class.java) { publication ->
                            publication.from(component)
                            createdPublications += publication
                        }
                        project.logger.debug("Created new publication $name")
                    }
                }
            }
            project.components.forEach(::createPublications)
            project.components.whenObjectAdded(::createPublications)
        }
        project.afterEvaluate {
            if (extension.autoConfigureAllPublications.orNull == true) {
                project.the<PublishingExtension>().publications.withType<MavenPublication>().configureEach {
                    it.configureForMavenCentral(extension)
                }
            } else {
                createdPublications.forEach { it.configureForMavenCentral(extension) }
            }
        }
        project.afterEvaluate {
            if (extension.configureMavenCentral.getOrElse(true)) {
                project.configureRepository(extension.mavenCentral)
            }
        }
        project.plugins.withType(JavaPlugin::class.java).configureEach { _ ->
            project.tasks.withType(JavadocJar::class.java).configureEach { javadocJar ->
                val javadocTask = project.tasks.findByName("javadoc") as? Javadoc
                    ?: throw IllegalStateException("Java plugin applied but no Javadoc task existing!")
                javadocJar.dependsOn(javadocTask)
                javadocJar.from(javadocTask.destinationDir)
            }
            project.tasks.withType(JarTasks::class.java).configureEach { it.sourceSet("main", true) }
        }
        val dokkaPluginClass = kotlin.runCatching { Class.forName("org.jetbrains.dokka.gradle.DokkaPlugin") }
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
