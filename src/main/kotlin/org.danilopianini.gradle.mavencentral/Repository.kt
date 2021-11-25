package org.danilopianini.gradle.mavencentral

import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure
import java.net.URI
import java.util.Locale

/**
 * A class modelling the concept of target Maven repository.
 * Includes a [name], an [url], and methods to compute [user] and [password] given a [Project].
 * If the repository is managed with Sonatype Nexus, then the Nexus uri should be provided as [nexusUrl].
 */
data class Repository(
    val name: String,
    val url: String,
    val user: Project.() -> String?,
    val password: Project.() -> String?,
    val nexusUrl: String? = null,
) {

    /**
     * Same as [name], but capitalized.
     */
    val capitalizedName = name.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

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
        println("$name NEXUS URL: $nexusUrl")
        if (nexusUrl != null) {
            project.extensions.configure(NexusPublishExtension::class) { nexusPublishing ->
                println("COnfiguring NexusPublishing: $nexusPublishing")
                nexusPublishing.repositories { repository ->
                    repository.create(name) {
                        println("CREATED REPO ${it.name}")
                        it.nexusUrl.set(project.uri(nexusUrl))
                        it.username.set(project.provider { user(project) })
                        it.password.set(project.provider { password(project) })
                    }
                }
            }
//            project.rootProject.afterEvaluate { rootProject ->
//
//                val initializeTask = rootProject.tasks.named<InitializeNexusStagingRepository>(
//                    "initialize${capitalizedName}StagingRepository"
//                )
//                val closeTask = rootProject.tasks.named<CloseNexusStagingRepository>(
//                    "close${capitalizedName}StagingRepository"
//                )
//                val releaseTask = rootProject.tasks.named<ReleaseNexusStagingRepository>(
//                    "release${capitalizedName}StagingRepository"
//                )
//            }
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
