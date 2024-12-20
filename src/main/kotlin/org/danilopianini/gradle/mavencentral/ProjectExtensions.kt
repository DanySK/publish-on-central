package org.danilopianini.gradle.mavencentral

import io.github.gradlenexus.publishplugin.internal.StagingRepository.State.CLOSED
import kotlinx.coroutines.runBlocking
import org.danilopianini.gradle.mavencentral.MavenPublicationExtensions.signingTasks
import org.danilopianini.gradle.mavencentral.PublishPortalDeployment.Companion.DROP_TASK_NAME
import org.danilopianini.gradle.mavencentral.PublishPortalDeployment.Companion.RELEASE_TASK_NAME
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension
import kotlin.reflect.KClass

internal object ProjectExtensions {
    /**
     * The id of the Kotlin/JS plugin.
     */
    private const val KOTLIN_JS_PLUGIN = "org.jetbrains.kotlin.js"

    /**
     * The `jsSourcesJar` [Task] of a Kotlin/JS project.
     */
    internal val Project.jsSourcesJar: Jar? get() = tasks.withType<Jar>().findByName("jsSourcesJar")

    /**
     * The `kotlinSourcesJar` [Task] of a Kotlin/JS or Kotlin/JVM project.
     */
    private val Project.kotlinSourcesJar: Jar? get() = tasks.withType<Jar>().findByName("kotlinSourcesJar")

    /**
     * The list of default sources Jar [Task]s: it may include [kotlinSourcesJar] and [jsSourcesJar],
     * if they are non-null.
     */
    internal val Project.sourcesJarTasks: List<Jar> get() = listOfNotNull(jsSourcesJar, kotlinSourcesJar)

    /**
     * Executes an action on Kotlin/JS projects only.
     */
    internal fun Project.ifKotlinJsProject(action: Action<Plugin<*>>) {
        plugins.withId(KOTLIN_JS_PLUGIN, action)
    }

    /**
     * Configures the provided task to include the `main` source set of a Kotlin/JS project.
     * The configuration does nothing if the provided task not of type [SourceJar].
     */
    fun Project.configureJavadocJarTaskForKtJs(sourcesJarTask: Task) {
        ifKotlinJsProject { _ ->
            configure<KotlinJsProjectExtension> {
                sourceSets.getByName("main") {
                    (sourcesJarTask as? SourceJar)?.run {
                        sourceSet(it.kotlin)
                        sourceSet(it.resources)
                    } ?: logger.warn(
                        "source sets of task {} not configured because it is not of type {}",
                        sourcesJarTask.name,
                        SourceJar::class.java.name,
                    )
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
        if (repoToConfigure.nexusUrl != null) {
            configureNexusRepository(repoToConfigure, repoToConfigure.nexusUrl)
        }
    }

    private fun Project.configureNexusRepository(
        repoToConfigure: Repository,
        nexusUrl: String,
    ) {
        val repoName = repoToConfigure.name
        val nexusClient =
            rootProject.registerTaskIfNeeded(
                "createNexusClientFor$repoName",
                InitializeNexusClient::class,
                repoToConfigure,
                nexusUrl,
            ) as InitializeNexusClient
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
                dependsOn(nexusClient)
                doLast {
                    rootProject.warnIfCredentialsAreMissing(repoToConfigure)
                    nexusClient.nexusClient.repoUrl // triggers the initialization of a repository
                    val repoId = nexusClient.nexusClient.repoId
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
                    with(nexusClient.nexusClient) {
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
        val drop =
            rootProject.registerTaskIfNeeded("dropStagingRepositoryOn${repoToConfigure.name}") {
                doLast { nexusClient.nexusClient.drop() }
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
            if (it.hasTask(release) && it.hasTask(drop)) {
                error("Mutually exclusive tasks '${release.name}' and '${drop.name}' both selected for execution")
            }
        }
        the<PublishingExtension>().publications.withType<MavenPublication>().configureEach { publication ->
            val publicationName = publication.name.replaceFirstChar(Char::titlecase)
            project.tasks
                .register<PublishToMavenRepository>(
                    "upload${publicationName}To${repoToConfigure.name}Nexus",
                ).configure { uploadTask ->
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
    ): Task =
        registerTaskIfNeeded(
            name = name,
            type = DefaultTask::class,
            parameters = parameters,
            configuration = configuration,
        )

    internal fun Project.addSourcesArtifactIfNeeded(
        publication: MavenPublication,
        sourcesJarTask: Task,
    ) {
        if (sourcesJarTask is SourceJar) {
            if (jsSourcesJar == null) {
                publication.artifact(sourcesJarTask)
                logger.info(
                    "add sources jar artifact to publication {} from task {}",
                    publication.name,
                    sourcesJarTask.name,
                )
            } else {
                /*
                 * This is a hack for Kotlin/JS projects.
                 * These projects already contain tasks named "jsSourcesJar" and "kotlinSourcesJar", generating the
                 * same jar "<project.name>-js-<project.version>-sources.jar".
                 * In particular, task kotlinSourcesJar is automatically registered as an artifact to Maven publications
                 * when they are created. So, adding further sources-jar-generating tasks it troublesome in this
                 * situation. The following code simply removes the "-js" appendix from the jar file name,
                 * hence making the jar compliant with Maven Central.
                 */
                ifKotlinJsProject { _ ->
                    project.sourcesJarTasks.forEach {
                        it.archiveAppendix.set("")
                        // Better telling the user the plugin is changing the behaviour of default tasks
                        project.logger.lifecycle(
                            "remove '-js' appendix from sources jar generated by task {}",
                            it.name,
                        )
                    }
                }
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
