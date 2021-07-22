package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import java.net.URI

/**
 * A class modelling the concept of target Maven repository.
 * Includes a [name], an [url], and methods to compute [user] and [password] given a [Project].
 */
data class Repository(
    val name: String,
    val url: String,
    val user: Project.() -> String?,
    val password: Project.() -> String?,
) {
    override fun toString() = "$name at $url"

    fun configureForProject(project: Project) {
        project.extensions.configure(PublishingExtension::class.java) { publishing ->
            publishing.repositories { repository ->
                repository.maven { mavenArtifactRepository ->
                    mavenArtifactRepository.name = name
                    mavenArtifactRepository.url = URI(url)
                    mavenArtifactRepository.credentials { credentials ->
                        credentials.username = project.user()
                        credentials.password = project.password()
                        credentials.username
                            ?: project.logger.warn("No username configured for $name at $url.")
                        credentials.password
                            ?: project.logger.warn("No password configured for $name at $url.")
                    }
                }
            }
        }
    }

    companion object {
        /**
         * The pre-configured Maven Central repository
         */
        val mavenCentral = Repository(
            "MavenCentral",
            url = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/",
            user = {
                System.getenv("MAVEN_CENTRAL_USERNAME")
                    ?: this.properties["mavenCentralUsername"]?.toString()
            },
            password = {
                System.getenv("MAVEN_CENTRAL_PASSWORD")
                    ?: project.properties["mavenCentralUsername"].toString()
            }
        )
    }
}
