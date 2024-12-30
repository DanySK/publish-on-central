package org.danilopianini.gradle.mavencentral.nexus

import io.github.gradlenexus.publishplugin.internal.StagingRepository.State.CLOSED
import org.danilopianini.gradle.mavencentral.ProjectExtensions.registerTaskIfNeeded
import org.danilopianini.gradle.mavencentral.ProjectExtensions.warnIfCredentialsAreMissing
import org.danilopianini.gradle.mavencentral.Repository
import org.danilopianini.gradle.mavencentral.tasks.InitializeNexusClient
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign

internal object NexusSupport {
    fun Project.configureNexusRepository(
        repoToConfigure: Repository,
        nexusUrl: String,
    ) {
        val repoName = repoToConfigure.name
        val nexusClientCreationTask =
            rootProject.registerTaskIfNeeded(
                "createNexusClientFor$repoName",
                InitializeNexusClient::class,
                repoToConfigure,
                nexusUrl,
            )

        fun nexusClient() = (nexusClientCreationTask.get() as InitializeNexusClient).nexusClient
        /*
         * Creates a new staging repository on the Nexus server, or fetches an existing one if the repoId is known.
         */
        val createStagingRepository =
            rootProject.registerTaskIfNeeded(
                "createStagingRepositoryOn$repoName",
            ) {
                val stagingRepoIdsFileName = "staging-repo-ids.properties"
                val stagingRepoIdsFile =
                    rootProject.layout.buildDirectory.map { it.asFile.resolve(stagingRepoIdsFileName) }
                outputs.file(stagingRepoIdsFile)
                dependsOn(nexusClientCreationTask)
                doLast {
                    rootProject.warnIfCredentialsAreMissing(repoToConfigure)
                    nexusClient().repoUrl // triggers the initialization of a repository
                    val repoId = nexusClient().repoId
                    // Write the staging repository ID to build/staging-repo-ids.properties file
                    stagingRepoIdsFile.get().appendText("$repoName=$repoId" + System.lineSeparator())
                    logger.lifecycle("Append repo name {} to file {}", repoId, stagingRepoIdsFile.get().path)
                }
                group = PublishingPlugin.PUBLISH_TASK_GROUP
                description = "Creates a new Nexus staging repository on $repoName."
            }
        /*
         * Collector of all upload tasks. Actual uploads are defined at the bottom.
         * Requires the creation of the staging repository.
         */
        val uploadAllPublications =
            tasks.register("uploadAllPublicationsTo${repoName}Nexus") {
                it.dependsOn(createStagingRepository)
                it.group = PublishingPlugin.PUBLISH_TASK_GROUP
                it.description = "Uploads all publications to a staging repository on $repoName."
            }
        /*
         * Closes the staging repository. If it's closed already, skips the operation.
         * Runs after all uploads. Requires the creation of the staging repository.
         */
        val closeStagingRepository =
            rootProject.registerTaskIfNeeded("closeStagingRepositoryOn$repoName") {
                doLast {
                    with(nexusClient()) {
                        when (client.getStagingRepositoryStateById(repoId).state) {
                            CLOSED -> logger.warn("The staging repository is already closed. Skipping.")
                            else -> close()
                        }
                    }
                }
                dependsOn(createStagingRepository)
                mustRunAfter(uploadAllPublications)
                group = PublishingPlugin.PUBLISH_TASK_GROUP
                description = "Closes the Nexus repository on $repoName."
            }
        /*
         * Releases the staging repository. Requires closing.
         */
        val release =
            rootProject.registerTaskIfNeeded("releaseStagingRepositoryOn${repoToConfigure.name}") {
                doLast { nexusClient().release() }
                dependsOn(closeStagingRepository)
                group = PublishingPlugin.PUBLISH_TASK_GROUP
                description = "Releases the Nexus repo on ${repoToConfigure.name}. " +
                    "Mutually exclusive with dropStagingRepositoryOn${repoToConfigure.name}."
            }
        /*
         * Drops the staging repository.
         * Requires the creation of the staging repository.
         * It must run after all uploads.
         * If closing is requested as well, drop must run after it.
         */
        val drop =
            rootProject.registerTaskIfNeeded("dropStagingRepositoryOn${repoToConfigure.name}") {
                doLast { nexusClient().drop() }
                dependsOn(createStagingRepository)
                mustRunAfter(uploadAllPublications)
                mustRunAfter(closeStagingRepository)
                group = PublishingPlugin.PUBLISH_TASK_GROUP
                description =
                    "Drops the Nexus repo on ${repoToConfigure.name}. Incompatible with releasing the same repo."
            }
        /*
         * Checks that only release or drop are selected for execution, as they are mutually exclusive.
         */
        gradle.taskGraph.whenReady {
            val releaseTask = release.get()
            val dropTask = drop.get()
            if (it.hasTask(releaseTask) && it.hasTask(dropTask)) {
                error(
                    "Mutually exclusive tasks '${releaseTask.name}' and '${dropTask.name}' " +
                        "are both selected for execution",
                )
            }
        }
        the<PublishingExtension>().publications.withType<MavenPublication>().configureEach { publication ->
            val publicationName = publication.name.replaceFirstChar(Char::titlecase)
            val uploadTaskProvider =
                project.tasks
                    .register<PublishToMavenRepository>(
                        "upload${publicationName}To${repoToConfigure.name}Nexus",
                    )
            uploadTaskProvider.configure { uploadTask ->
                uploadTask.repository =
                    project.repositories.maven { repo ->
                        repo.name = repoToConfigure.name
                        repo.url = project.uri(repoToConfigure.url)
                        repo.credentials {
                            it.username = repoToConfigure.user.orNull
                            it.password = repoToConfigure.password.orNull
                        }
                    }
                uploadTask.publication = publication
                tasks.withType<Sign>().matching { publicationName in it.name }.forEach { uploadTask.dependsOn(it) }
                uploadTask.doFirst {
                    warnIfCredentialsAreMissing(repoToConfigure)
                    uploadTask.repository.url = nexusClient().repoUrl
                }
                uploadTask.group = PublishingPlugin.PUBLISH_TASK_GROUP
                uploadTask.description = "Uploads the $publicationName publication " +
                    "to a staging repository on ${repoToConfigure.name} (${repoToConfigure.url.orNull})."
            }
            /*
             * We need to make sure that the staging repository is created before we upload anything.
             * We also need to make sure that the staging repository is closed *after* we upload
             * We also need to make sure that the staging repository is dropped *after* we upload
             * Releasing does not need to be explicitly ordered, as it will be performed after closing
             */
            uploadTaskProvider.get().dependsOn(createStagingRepository)
            uploadAllPublications.get().dependsOn(uploadTaskProvider)
            closeStagingRepository.configure { it.mustRunAfter(uploadTaskProvider) }
            drop.configure { it.mustRunAfter(uploadTaskProvider) }
        }
    }
}
