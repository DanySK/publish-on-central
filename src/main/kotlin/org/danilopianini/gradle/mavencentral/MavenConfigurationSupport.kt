package org.danilopianini.gradle.mavencentral

import org.danilopianini.gradle.mavencentral.ProjectExtensions.warnIfCredentialsAreMissing
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Extension functions for org.gradle.api.publish.maven.MavenPublication to target Maven Central.
 */
object MavenConfigurationSupport {
    /**
     * Configures the pom.xml file of a [MavenPublication] with the information specified in this configuration.
     */
    fun MavenPublication.configurePomForMavenCentral(extension: PublishOnCentralExtension) {
        pom { pom ->
            with(pom) {
                name.set(extension.projectLongName)
                description.set(extension.projectDescription)
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
                    mavenArtifactRepository.url = repoToConfigure.url.get()
                    if (mavenArtifactRepository.url.scheme != "file") {
                        mavenArtifactRepository.credentials { credentials ->
                            credentials.username = repoToConfigure.user.orNull
                            credentials.password = repoToConfigure.password.orNull
                        }
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
    }
}
