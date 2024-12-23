package org.danilopianini.gradle.mavencentral

import io.github.gradlenexus.publishplugin.internal.StagingRepository.State.CLOSED
import kotlinx.coroutines.runBlocking
import org.danilopianini.gradle.mavencentral.MavenPublicationExtensions.signingTasks
import org.danilopianini.gradle.mavencentral.PublishPortalDeployment.Companion.DROP_TASK_NAME
import org.danilopianini.gradle.mavencentral.PublishPortalDeployment.Companion.RELEASE_TASK_NAME
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import kotlin.reflect.KClass

internal object ProjectExtensions {
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
        if (repoToConfigure.nexusUrl != null) {
            configureNexusRepository(repoToConfigure, repoToConfigure.nexusUrl)
        }
    }

    private fun Project.configureNexusRepository(
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
                publication.signingTasks(project).forEach { uploadTask.dependsOn(it) }
                tasks.withType<Sign>().forEach { uploadTask.mustRunAfter(it) }
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

    internal inline fun <reified T> Project.propertyWithDefault(default: T?): Property<T> =
        objects.property<T>().apply { convention(default) }

    internal inline fun <reified T> Project.propertyWithDefaultProvider(noinline default: () -> T?): Property<T> =
        objects.property<T>().apply { convention(provider(default)) }

    fun <T : Task> Project.registerTaskIfNeeded(
        name: String,
        type: KClass<T>,
        vararg parameters: Any = emptyArray(),
        configuration: T.() -> Unit = { },
    ): TaskProvider<out Task> =
        runCatching { tasks.named(name) }
            .recover { exception ->
                when (exception) {
                    is UnknownTaskException ->
                        tasks.register(name, type, *parameters).apply { configure(configuration) }
                    else -> throw exception
                }
            }.getOrThrow()

    fun Project.registerTaskIfNeeded(
        name: String,
        vararg parameters: Any = emptyArray(),
        configuration: DefaultTask.() -> Unit = { },
    ): TaskProvider<out Task> =
        registerTaskIfNeeded(
            name = name,
            type = DefaultTask::class,
            parameters = parameters,
            configuration = configuration,
        )

    internal fun Project.addSourcesArtifactIfNeeded(
        publication: MavenPublication,
        sourcesJarProvider: TaskProvider<out Task>,
    ) {
        sourcesJarProvider.configure { sourcesJarTask ->
            if (sourcesJarTask is SourceJar && tasks.withType<Jar>().named { it == "jsSourcesJar" }.isEmpty()) {
                publication.artifact(sourcesJarTask)
                logger.info(
                    "add sources jar artifact to publication {} from task {}",
                    publication.name,
                    sourcesJarTask.name,
                )
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

    internal fun Project.setupMavenCentralPortal() {
        configureRepository(Repository.projectLocalRepository(project))
        val zipMavenCentralPortal =
            tasks.register<ZipMavenCentralPortalPublication>(
                checkNotNull(ZipMavenCentralPortalPublication::class.simpleName)
                    .replaceFirstChar { it.lowercase() },
            )
        val portalDeployment =
            PublishPortalDeployment(
                project = project,
                baseUrl = "https://central.sonatype.com/",
                user =
                    project.propertyWithDefaultProvider {
                        System.getenv("MAVEN_CENTRAL_PORTAL_USERNAME")
                            ?: project.properties["mavenCentralPortalUsername"]?.toString()
                            ?: project.properties["centralPortalUsername"]?.toString()
                            ?: project.properties["centralUsername"]?.toString()
                    },
                password =
                    project.propertyWithDefaultProvider {
                        System.getenv("MAVEN_CENTRAL_PORTAL_PASSWORD")
                            ?: project.properties["mavenCentralPortalPassword"]?.toString()
                            ?: project.properties["centralPortalPassword"]?.toString()
                            ?: project.properties["centralPassword"]?.toString()
                    },
                zipTask = zipMavenCentralPortal,
            )
        tasks.register("saveMavenCentralPortalDeploymentId") { save ->
            val fileName = "maven-central-portal-bundle-id"
            val file = rootProject.layout.buildDirectory.map { it.asFile.resolve(fileName) }
            save.group = PublishingPlugin.PUBLISH_TASK_GROUP
            save.description = "Saves the Maven Central Portal deployment ID locally in ${file.get().absolutePath}"
            save.dependsOn(zipMavenCentralPortal)
            save.outputs.file(file)
            save.doLast {
                file.get().writeText("${portalDeployment.fileToUpload}=${portalDeployment.deploymentId}\n")
            }
        }
        val validate =
            tasks.register(PublishPortalDeployment.VALIDATE_TASK_NAME) { validate ->
                validate.group = PublishingPlugin.PUBLISH_TASK_GROUP
                validate.description = "Validates the Maven Central Portal publication, uploading if needed"
                validate.mustRunAfter(zipMavenCentralPortal)
                validate.doLast {
                    runBlocking {
                        portalDeployment.validate()
                    }
                }
            }
        tasks.register(DROP_TASK_NAME) { drop ->
            drop.group = PublishingPlugin.PUBLISH_TASK_GROUP
            drop.description = "Drops the Maven Central Portal publication"
            drop.mustRunAfter(validate)
            drop.mustRunAfter(zipMavenCentralPortal)
            drop.doLast {
                runBlocking {
                    portalDeployment.drop()
                }
            }
        }
        tasks.register(RELEASE_TASK_NAME) { release ->
            release.group = PublishingPlugin.PUBLISH_TASK_GROUP
            release.description = "Releases the Maven Central Portal publication"
            release.mustRunAfter(validate)
            release.mustRunAfter(zipMavenCentralPortal)
            release.doLast {
                runBlocking {
                    portalDeployment.release()
                }
            }
        }
        gradle.taskGraph.whenReady { taskGraph ->
            val allTasks = taskGraph.allTasks.map { it.name }.toSet()
            check(RELEASE_TASK_NAME !in allTasks || DROP_TASK_NAME !in allTasks) {
                "Task $RELEASE_TASK_NAME and $DROP_TASK_NAME cannot be executed together"
            }
        }
    }
}
