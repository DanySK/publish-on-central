package org.danilopianini.gradle.mavencentral

import io.github.gradlenexus.publishplugin.CloseNexusStagingRepository
import io.github.gradlenexus.publishplugin.InitializeNexusStagingRepository
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import java.net.URI

/**
 * A class modelling the concept of target Maven repository.
 * Includes a [name], an [url], and methods to compute [user] and [password] given a [Project].
 * If the repository is managed with Sonatype Nexus,
 * then the Nexus uri should be provided as [nexusUrl],
 * and the staging URL should be provided in [nexusStagingUrl].
 */
data class Repository(
    val name: String,
    val url: String,
    val user: Project.() -> String?,
    val password: Project.() -> String?,
    val nexusUrl: String? = null,
    val nexusStagingUrl: String? = null,
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
            project.extensions.configure(NexusPublishExtension::class) { nexusPublishing ->
                nexusPublishing.repositories { repository ->
                    repository.create(name) {
                        it.nexusUrl.set(project.uri(nexusUrl))
                        it.snapshotRepositoryUrl.set(project.uri(nexusStagingUrl ?: nexusUrl))
                        it.username.set(project.provider { user(project) })
                        it.password.set(project.provider { password(project) })
                    }
                }
            }
            project.rootProject.afterEvaluate { rootProject ->
                val initializeTask = rootProject.tasks.named<InitializeNexusStagingRepository>(
                    "initialize${capitalizedName}StagingRepository"
                )
                val closeTask = rootProject.tasks.named<CloseNexusStagingRepository>(
                    "close${capitalizedName}StagingRepository"
                )
                val releaseTask = rootProject.tasks.named<Task>(
                    "closeAndRelease${capitalizedName}StagingRepository"
                )
                rootProject.allprojects { subproject ->
                    subproject.tasks.withType<PublishToMavenRepository> {
                        if (repository.name == this@Repository.name) {
                            val publicationName = publication.name.replace("Maven", "Publication")
                                .replaceFirstChar(Char::titlecase)
                            mustRunAfter(initializeTask)
                            closeTask.get().mustRunAfter(this)
                            releaseTask.get().mustRunAfter(this)
                            val suffix = "${publicationName}On${capitalizedName}Nexus"
                            val closeTaskName = "close$suffix"
                            val releaseTaskName = "release$suffix"
                            val closePublicationTask = subproject.tasks.findByName(closeTaskName)
                                ?: subproject.tasks.register(closeTaskName).get()
                            val releasePublicationTask = subproject.tasks.findByName(releaseTaskName)
                                ?: subproject.tasks.register(releaseTaskName).get()
                            val descriptionSuffix = "the staging repository $capitalizedName" +
                                " after the upload of ${publication.name}"
                            closePublicationTask.description = "Closes $descriptionSuffix"
                            releasePublicationTask.description = "Closes and relases $descriptionSuffix"
                            listOf(closePublicationTask, releasePublicationTask).forEach {
                                it.group = PublishingPlugin.PUBLISH_TASK_GROUP
                                it.dependsOn(this)
                                it.dependsOn(closeTask)
                                it.dependsOn(initializeTask)
                            }
                            releasePublicationTask.dependsOn(closePublicationTask)
                            releasePublicationTask.dependsOn(releaseTask)
                        }
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
            nexusStagingUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
        )
    }
}
