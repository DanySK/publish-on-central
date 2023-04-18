package org.danilopianini.gradle.mavencentral

import io.github.gradlenexus.publishplugin.internal.StagingRepository
import org.danilopianini.gradle.mavencentral.MavenPublicationExtensions.signingTasks
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension
import java.net.URI
import kotlin.reflect.KClass

internal object ProjectExtensions {

    fun Project.configureJavadocJarTaskForKtJs(sourcesJarTask: Task) {
        plugins.withId("org.jetbrains.kotlin.js") { _ ->
            configure<KotlinJsProjectExtension> {
                js {
                    sourceSets.getByName("main") {
                        (sourcesJarTask as SourceJar).sourceSet(it.kotlin)
                        sourcesJarTask.sourceSet(it.resources)
                    }
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
                    mavenArtifactRepository.url = repoToConfigure.url.map { URI(it) }.get()
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
        val nexusClient = rootProject.registerTaskIfNeeded(
            "createNexusClientFor${repoToConfigure.name}",
            InitializeNexusClient::class,
            repoToConfigure,
            nexusUrl,
        ) as InitializeNexusClient
        /*
         * Creates a new staging repository on the Nexus server, or fetches an existing one if the repoId is known.
         */
        val createStagingRepository = rootProject.registerTaskIfNeeded(
            "createStagingRepositoryOn${repoToConfigure.name}"
        ) {
            val stagingRepoIdsFileName = "staging-repo-ids.properties"
            outputs.file(buildDir.resolve(stagingRepoIdsFileName))
            dependsOn(nexusClient)
            doLast {
                rootProject.warnIfCredentialsAreMissing(repoToConfigure)
                nexusClient.nexusClient.repoUrl // triggers the initialization of a repository
                // Write the staging repository ID to build/staging-repo-ids.properties file
                buildDir.resolve(stagingRepoIdsFileName).appendText(
                    "${repoToConfigure.name}=${nexusClient.nexusClient.repoId}" + System.lineSeparator()
                )
            }
            group = PublishingPlugin.PUBLISH_TASK_GROUP
            description = "Creates a new Nexus staging repository on ${repoToConfigure.name}."
        }
        /*
         * Collector of all upload tasks. Actual uploads are defined at the bottom.
         * Requires the creation of the staging repository.
         */
        val uploadAllPublications = tasks.register("uploadAllPublicationsTo${repoToConfigure.name}Nexus") {
            it.dependsOn(createStagingRepository)
            it.group = PublishingPlugin.PUBLISH_TASK_GROUP
            it.description = "Uploads all publications to a staging repository on ${repoToConfigure.name}."
        }
        /*
         * Closes the staging repository. If it's closed already, skips the operation.
         * Runs after all uploads. Requires the creation of the staging repository.
         */
        val closeStagingRepository = rootProject.registerTaskIfNeeded(
            "closeStagingRepositoryOn${repoToConfigure.name}"
        ) {
            doLast {
                with(nexusClient.nexusClient) {
                    when (client.getStagingRepositoryStateById(repoId).state) {
                        StagingRepository.State.CLOSED ->
                            logger.warn("The staging repository is already closed. Skipping.")
                        else -> close()
                    }
                }
            }
            dependsOn(createStagingRepository)
            mustRunAfter(uploadAllPublications)
            group = PublishingPlugin.PUBLISH_TASK_GROUP
            description = "Closes the Nexus repository on ${repoToConfigure.name}."
        }
        /*
         * Releases the staging repository. Requires closing.
         */
        val release = rootProject.registerTaskIfNeeded("releaseStagingRepositoryOn${repoToConfigure.name}") {
            doLast { nexusClient.nexusClient.release() }
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
        val drop = rootProject.registerTaskIfNeeded("dropStagingRepositoryOn${repoToConfigure.name}") {
            doLast { nexusClient.nexusClient.drop() }
            dependsOn(createStagingRepository)
            mustRunAfter(uploadAllPublications)
            mustRunAfter(closeStagingRepository)
            group = PublishingPlugin.PUBLISH_TASK_GROUP
            description = "Drops the Nexus repo on ${repoToConfigure.name}. Incompatible with releasing the same repo."
        }
        /*
         * Checks that only release or drop are selected for execution, as they are mutually exclusive.
         */
        gradle.taskGraph.whenReady {
            if (it.hasTask(release) && it.hasTask(drop)) {
                error(
                    "Tasks ${release.name} and ${drop.name} have both been selected for execution, " +
                        "but they are mutually exclusive"
                )
            }
        }
        the<PublishingExtension>().publications.withType<MavenPublication>().configureEach { publication ->
            val publicationName = publication.name.replaceFirstChar(Char::titlecase)
            project.tasks.register<PublishToMavenRepository>(
                "upload${publicationName}To${repoToConfigure.name}Nexus",
            ).configure { uploadTask ->
                uploadTask.repository = project.repositories.maven { repo ->
                    repo.name = repoToConfigure.name
                    repo.url = project.uri(repoToConfigure.url)
                    repo.credentials {
                        it.username = repoToConfigure.user.orNull
                        it.password = repoToConfigure.password.orNull
                    }
                }
                uploadTask.publication = publication
                publication.signingTasks(project).forEach {
                    uploadTask.dependsOn(it)
                }
                tasks.withType<Sign>().forEach {
                    uploadTask.mustRunAfter(it)
                }
                /*
                 * We need to make sure that the staging repository is created before we upload anything.
                 * We also need to make sure that the staging repository is closed *after* we upload
                 * We also need to make sure that the staging repository is dropped *after* we upload
                 * Releasing does not need to be explicitly ordered, as it will be performed after closing
                 */
                uploadTask.dependsOn(createStagingRepository)
                uploadAllPublications.get().dependsOn(uploadTask)
                closeStagingRepository.mustRunAfter(uploadTask)
                drop.mustRunAfter(uploadTask)
                uploadTask.doFirst {
                    warnIfCredentialsAreMissing(repoToConfigure)
                    uploadTask.repository.url = nexusClient.nexusClient.repoUrl
                }
                uploadTask.group = PublishingPlugin.PUBLISH_TASK_GROUP
                uploadTask.description = "Uploads the $publicationName publication " +
                    "to a staging repository on ${repoToConfigure.name} (${repoToConfigure.url.orNull})."
            }
        }
    }

    internal inline fun <reified T> Project.propertyWithDefault(default: T?): Property<T> =
        objects.property<T>().apply { convention(default) }

    internal inline fun <reified T> Project.propertyWithDefaultProvider(noinline default: () -> T?): Property<T> =
        objects.property<T>().apply { convention(provider(default)) }

    fun <T : Task> Project.registerTaskIfNeeded(
        name: String,
        type: KClass<T>,
        vararg parameters: Any = emptyArray(),
        configuration: T.() -> Unit = { },
    ): Task = tasks.findByName(name) ?: tasks.create(name, type, *parameters).apply(configuration)

    fun Project.registerTaskIfNeeded(
        name: String,
        vararg parameters: Any = emptyArray(),
        configuration: DefaultTask.() -> Unit = { },
    ): Task = registerTaskIfNeeded(
        name = name,
        type = DefaultTask::class,
        parameters = parameters,
        configuration = configuration,
    )

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
}
