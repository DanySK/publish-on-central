package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import java.net.URI

internal inline fun <reified T> Project.propertyWithDefault(default: T?): Property<T> =
    objects.property(T::class.java).apply { convention(default) }

internal inline fun <reified T> Project.propertyWithDefaultProvider(noinline default: () -> T?): Property<T> =
    objects.property(T::class.java).apply { convention(provider(default)) }

/**
 * Configures a [MavenPublication] for publication on Maven Central, adding the following.
 * - appropriate pom.xml configuration
 * - a main jar file
 * - a source jar file
 * - a javadoc jar file
 */
fun MavenPublication.configureForMavenCentral(extension: PublishOnCentralExtension) {
    configurePomForMavenCentral(extension)
    val project = extension.project
    // Signing
    project.tasks.findByName("sign${name.capitalized()}Publication")
        ?: project.configure<SigningExtension> {
            sign(this@configureForMavenCentral)
        }
}

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
 * Reifies this repository setup onto every [PublishingExtension] configuration of the provided [Project].
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
                }
                tasks.withType(PublishToMavenRepository::class) {
                    if (it.repository == mavenArtifactRepository) {
                        it.doFirst {
                            warnIfCredentialsAreMissing(repoToConfigure)
                        }
                    }
                }
            }
        }
    }
    if (repoToConfigure.nexusUrl != null) {
        configureNexusRepository(repoToConfigure, repoToConfigure.nexusUrl)
    }
}

private fun Project.configureNexusRepository(repoToConfigure: Repository, nexusUrl: String) {
    the<PublishingExtension>().publications.configureEach {
        println("Publication ${it.name}: ${it::class}")
    }
    val nexus = NexusStatefulOperation(
        project = project,
        nexusUrl = nexusUrl,
        group = project.group.toString(),
        user = repoToConfigure.user,
        password = repoToConfigure.password,
        timeOut = repoToConfigure.nexusTimeOut,
        connectionTimeOut = repoToConfigure.nexusConnectTimeOut,
    )
    val nexusUploadUrl: Property<URI> = project.objects.property()
    val createStagingRepository = tasks.register("createStagingRepositoryOn${repoToConfigure.name}") {
        it.doLast {
            project.warnIfCredentialsAreMissing(repoToConfigure)
            nexusUploadUrl.set(nexus.repoUrl)
        }
        it.group = PublishingPlugin.PUBLISH_TASK_GROUP
        it.description = "Creates a new Nexus staging repository on ${repoToConfigure.name}."
    }
    val uploadAllPublications = tasks.register("uploadAllPublicationsTo${repoToConfigure.name}Nexus") {
        it.dependsOn(createStagingRepository)
        it.group = PublishingPlugin.PUBLISH_TASK_GROUP
        it.description = "Uploads all publications to a staging repository on ${repoToConfigure.name}."
    }
    val closeStagingRepository = tasks.register("closeStagingRepositoryOn${repoToConfigure.name}") {
        it.doLast { nexus.close() }
        it.dependsOn(createStagingRepository)
        it.mustRunAfter(uploadAllPublications)
        it.group = PublishingPlugin.PUBLISH_TASK_GROUP
        it.description = "Closes the Nexus repository on ${repoToConfigure.name}."
    }
    tasks.register("releaseStagingRepositoryOn${repoToConfigure.name}") {
        it.doLast { nexus.release() }
        it.dependsOn(closeStagingRepository)
        it.group = PublishingPlugin.PUBLISH_TASK_GROUP
        it.description = "Releases the Nexus repo on ${repoToConfigure.name}."
    }
    the<PublishingExtension>().publications.withType<MavenPublication>().configureEach { publication ->
        val publicationName = publication.name.replaceFirstChar(Char::titlecase)
        project.tasks.register<PublishToMavenRepository>(
            "upload${publicationName}To${repoToConfigure.name}Nexus",
        ).configure { publishTask ->
            publishTask.repository = project.repositories.maven { repo ->
                repo.name = repoToConfigure.name
                repo.url = project.uri(repoToConfigure.url)
                repo.credentials {
                    it.username = repoToConfigure.user.orNull
                    it.password = repoToConfigure.password.orNull
                }
            }
            publishTask.dependsOn(createStagingRepository)
            uploadAllPublications.get().dependsOn(publishTask)
            closeStagingRepository.get().mustRunAfter(publishTask)
            publishTask.publication = publication
            publishTask.doFirst {
                warnIfCredentialsAreMissing(repoToConfigure)
            }
            publishTask.doLast {
                publishTask.repository.url = nexusUploadUrl.get()
            }
            publishTask.group = PublishingPlugin.PUBLISH_TASK_GROUP
            publishTask.description = "Uploads the $publicationName publication " +
                "to a staging repository on ${repoToConfigure.name}."
        }
    }
}

private fun Project.warnIfCredentialsAreMissing(repository: Repository) {
    if (repository.user.orNull == null) {
        logger.warn(
            "No username configured for repository {} at {}.",
            repository.name,
            repository.url,
        )
    }
    if (repository.password.orNull == null) {
        logger.warn(
            "No password configured for user {} on repository {} at {}.",
            repository.user.orNull,
            repository.name,
            repository.url,
        )
    }
}
