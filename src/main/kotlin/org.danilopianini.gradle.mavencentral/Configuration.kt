package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import java.net.URI

internal inline fun <reified T> Project.propertyWithDefault(default: T?): Property<T> =
    objects.property(T::class.java).apply { convention(default) }

internal inline fun <reified T> Project.propertyWithDefaultProvider(noinline default: () -> T?): Property<T> =
    objects.property(T::class.java).apply { convention(provider(default)) }

/**
 * Configures the pom.xml file of a [MavenPublication] with the information specified in this configuration.
 */
fun MavenPublication.configurePomForMavenCentral(extension: PublishOnCentralExtension) {
    pom { pom ->
        with(pom) {
            name.set(extension.projectLongName)
            description.set(extension.projectDescription)
            packaging = "jar"
            url.set(extension.projectUrl)
            licenses {
                it.license { license ->
                    license.name.set(extension.licenseName)
                    license.url.set(extension.licenseUrl)
                }
            }
            scm { scm ->
                scm.url.set(extension.projectUrl)
                scm.connection.set(extension.scmConnection)
                scm.developerConnection.set(extension.scmConnection)
            }
        }
    }
}

/**
 * Reifies this repository setup onto every [PublishingExtension] configuration of the provided [project].
 */
fun Project.configureRepository(repoToConfigure: Repository) {
    extensions.configure(PublishingExtension::class) { publishing ->
        publishing.repositories { repository ->
            repository.maven { mavenArtifactRepository ->
                mavenArtifactRepository.name = repoToConfigure.name
                mavenArtifactRepository.url = URI(repoToConfigure.url)
                mavenArtifactRepository.credentials { credentials ->
                    credentials.username = repoToConfigure.user.orNull
                    credentials.password = repoToConfigure.password.orNull
                    credentials.username ?: project.logger.warn(
                        "No username configured for repository {} at {}.",
                        repoToConfigure.name,
                        repoToConfigure.url,
                    )
                    credentials.password ?: project.logger.warn(
                        "No password configured for user {} on repository {} at {}.",
                        repoToConfigure.user,
                        repoToConfigure.name,
                        repoToConfigure.url,
                    )
                }
            }
        }
    }
    if (repoToConfigure.nexusUrl != null) {
        configureNexusRepository(repoToConfigure, repoToConfigure.nexusUrl)
    }
}

private fun Project.configureNexusRepository(repoToConfigure: Repository, nexusUrl: String) {
    the<PublishingExtension>().publications.withType<MavenPublication>().forEach { publication ->
        val nexus = NexusStatefulOperation(
            project = project,
            nexusUrl = nexusUrl,
            group = project.group.toString(),
            user = repoToConfigure.user,
            password = repoToConfigure.password,
            timeOut = repoToConfigure.nexusTimeOut,
            connectionTimeOut = repoToConfigure.nexusConnectTimeOut,
        )
        val publicationName = publication.name.replaceFirstChar(Char::titlecase)
        val uploadArtifacts = project.tasks.create(
            "upload${publicationName}To${repoToConfigure.name}Nexus",
            PublishToMavenRepository::class,
        ) { publishTask ->
            publishTask.repository = project.repositories.maven { repo ->
                repo.name = repoToConfigure.name
                repo.url = project.uri(repoToConfigure.url)
                repo.credentials {
                    it.username = repoToConfigure.user.orNull
                    it.password = repoToConfigure.password.orNull
                }
            }
            publishTask.doFirst {
                publishTask.repository.url = nexus.repoUrl
            }
            publishTask.publication = publication
            publishTask.group = PublishingPlugin.PUBLISH_TASK_GROUP
            publishTask.description = "Initializes a new Nexus repository on ${repoToConfigure.name} " +
                "and uploads the $publicationName publication."
        }
        val closeRepository = tasks.create("close${publicationName}On${repoToConfigure.name}Nexus") {
            it.doLast { nexus.close() }
            it.dependsOn(uploadArtifacts)
            it.group = PublishingPlugin.PUBLISH_TASK_GROUP
            it.description = "Closes the Nexus repository on ${repoToConfigure.name} with the " +
                "$publicationName publication."
        }
        tasks.create("release${publicationName}On${repoToConfigure.name}Nexus") {
            it.doLast { nexus.release() }
            it.dependsOn(closeRepository)
            it.group = PublishingPlugin.PUBLISH_TASK_GROUP
            it.description = "Releases the Nexus repo on ${repoToConfigure.name} " +
                "with the $publicationName publication."
        }
    }
}
