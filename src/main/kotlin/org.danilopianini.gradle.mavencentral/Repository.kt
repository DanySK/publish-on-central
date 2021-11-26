package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import java.net.URI
import java.time.Duration

/**
 * A class modelling the concept of target Maven repository.
 * Includes a [name], an [url], and methods to compute [user] and [password] given a [Project].
 * If the repository is managed with Sonatype Nexus,
 * then the Nexus uri should be provided as [nexusUrl].
 * Time outs can be set with [nexusTimeOut] and [nexusConnectTimeOut].
 */
data class Repository(
    val name: String,
    val url: String,
    val user: Project.() -> String?,
    val password: Project.() -> String?,
    val nexusUrl: String? = null,
    val nexusTimeOut: Duration = Duration.ofMinutes(3),
    val nexusConnectTimeOut: Duration = Duration.ofMinutes(3),
) {

    /**
     * Same as [name], but capitalized.
     */
    val capitalizedName = name.replaceFirstChar(Char::titlecase)

    override fun toString() = "$name at $url"

    /**
     * Reifies this repository setup onto every [PublishingExtension] configuration of the provided [project].
     */
    fun configureForProject(project: Project) {
        project.extensions.configure(PublishingExtension::class) { publishing ->
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
        if (nexusUrl != null) {
            project.afterEvaluate {
                project.the<PublishingExtension>().publications.withType<MavenPublication>().forEach { publication ->
                    val nexus = NexusStatefulOperation(
                        project = project,
                        nexusUrl = nexusUrl,
                        group = project.group.toString(),
                        user = project.provider { user(project) },
                        password = project.provider { password(project) },
                        timeOut = nexusTimeOut,
                        connectionTimeOut = nexusConnectTimeOut,
                    )
                    val publicationName = publication.name.replaceFirstChar(Char::titlecase)
                    val uploadArtifacts = project.tasks.create(
                        "upload${publicationName}To${name}Nexus",
                        PublishToMavenRepository::class,
                    ) { publishTask ->
                        publishTask.repository = project.repositories.maven {
                            it.name = name
                            it.setUrl { nexus.repoUrl }
                        }
                        publishTask.publication = publication
                        publishTask.group = PublishingPlugin.PUBLISH_TASK_GROUP
                        publishTask.description = "Initializes a new Nexus repository on $name and uploads the " +
                            "$publicationName publication."
                    }
                    val closeRepository = project.tasks.create("close${publicationName}On${name}Nexus") {
                        it.doLast { nexus.close() }
                        it.dependsOn(uploadArtifacts)
                        it.group = PublishingPlugin.PUBLISH_TASK_GROUP
                        it.description = "Closes the Nexus repository on $name with the $publicationName publication."
                    }
                    project.tasks.create("release${publicationName}On${name}Nexus") {
                        it.doLast { nexus.release() }
                        it.dependsOn(closeRepository)
                        it.group = PublishingPlugin.PUBLISH_TASK_GROUP
                        it.description = "Releases the Nexus repo on $name with the $publicationName publication."
                    }
                }
            }
        }
    }

    companion object {
        /**
         * The pre-configured Maven Central repository.
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
            },
            nexusUrl = "https://s01.oss.sonatype.org/service/local/",
        )
    }
}
