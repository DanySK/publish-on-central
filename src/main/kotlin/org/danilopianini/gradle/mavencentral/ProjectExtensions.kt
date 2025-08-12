package org.danilopianini.gradle.mavencentral

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.danilopianini.gradle.mavencentral.MavenConfigurationSupport.configureRepository
import org.danilopianini.gradle.mavencentral.portal.PublishPortalDeployment
import org.danilopianini.gradle.mavencentral.portal.PublishPortalDeployment.Companion.DROP_TASK_NAME
import org.danilopianini.gradle.mavencentral.portal.PublishPortalDeployment.Companion.RELEASE_TASK_NAME
import org.danilopianini.gradle.mavencentral.tasks.ZipMavenCentralPortalPublication
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.provider.Property
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register

internal object ProjectExtensions {
    inline fun <reified T : Any> Project.propertyWithDefault(default: T?): Property<T> =
        objects.property<T>().apply { convention(default) }

    inline fun <reified T : Any> Project.propertyWithDefaultProvider(noinline default: () -> T?): Property<T> =
        objects.property<T>().apply { convention(provider(default)) }

    fun <T : Task> Project.registerTaskIfNeeded(
        name: String,
        type: KClass<T>,
        vararg parameters: Any = emptyArray(),
        configuration: T.() -> Unit = { },
    ): TaskProvider<out Task> = runCatching { tasks.named(name) }
        .recover { exception ->
            when (exception) {
                is UnknownTaskException ->
                    tasks.register(name, type, *parameters).apply { configure(configuration) }
                else -> throw exception
            }
        }.getOrThrow()

    fun Project.warnIfCredentialsAreMissing(repository: Repository) {
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

    fun Project.setupMavenCentralPortal() {
        configureRepository(Repository.projectLocalRepository(project))
        val zipMavenCentralPortal: TaskProvider<ZipMavenCentralPortalPublication> =
            tasks.register<ZipMavenCentralPortalPublication>(
                checkNotNull(ZipMavenCentralPortalPublication::class.simpleName)
                    .replaceFirstChar { it.lowercase() },
            )
        val portalDeployment = PublishPortalDeployment(
            project = project,
            baseUrl = "https://central.sonatype.com/",
            user =
            project.propertyWithDefaultProvider {
                System.getenv("MAVEN_CENTRAL_PORTAL_USERNAME")
                    ?: System.getenv("MAVEN_CENTRAL_USERNAME")
                    ?: project.properties["mavenCentralPortalUsername"]?.toString()
                    ?: project.properties["centralPortalUsername"]?.toString()
                    ?: project.properties["centralUsername"]?.toString()
            },
            password =
            project.propertyWithDefaultProvider {
                System.getenv("MAVEN_CENTRAL_PORTAL_PASSWORD")
                    ?: System.getenv("MAVEN_CENTRAL_PASSWORD")
                    ?: project.properties["mavenCentralPortalPassword"]?.toString()
                    ?: project.properties["centralPortalPassword"]?.toString()
                    ?: project.properties["centralPassword"]?.toString()
            },
            zipTask = zipMavenCentralPortal,
        )
        fun Task.ifThereAreInputFiles(block: suspend CoroutineScope.() -> Unit) = doLast {
            when {
                zipMavenCentralPortal.get().inputs.files.isEmpty -> project.logger.lifecycle(
                    "No input files available for ${project.name}'s Maven Publication, skipping $name",
                )
                else -> runBlocking(block = block)
            }
        }
        tasks.register("saveMavenCentralPortalDeploymentId") { save ->
            val fileName = "maven-central-portal-bundle-id"
            val file = project.layout.buildDirectory.map { it.asFile.resolve(fileName) }
            save.group = PublishingPlugin.PUBLISH_TASK_GROUP
            save.description = "Saves the Maven Central Portal deployment ID locally"
            save.dependsOn(zipMavenCentralPortal)
            save.outputs.file(file)
            save.ifThereAreInputFiles { file.get().writeText(portalDeployment.deploymentId) }
        }
        val validate = tasks.register(PublishPortalDeployment.VALIDATE_TASK_NAME) { validate ->
            validate.group = PublishingPlugin.PUBLISH_TASK_GROUP
            validate.description = "Validates the Maven Central Portal publication, uploading if needed"
            validate.mustRunAfter(zipMavenCentralPortal)
            validate.ifThereAreInputFiles { portalDeployment.validate() }
        }
        tasks.register(DROP_TASK_NAME) { drop ->
            drop.group = PublishingPlugin.PUBLISH_TASK_GROUP
            drop.description = "Drops the Maven Central Portal publication"
            drop.mustRunAfter(validate)
            drop.mustRunAfter(zipMavenCentralPortal)
            drop.ifThereAreInputFiles { portalDeployment.drop() }
        }
        tasks.register(RELEASE_TASK_NAME) { release ->
            release.group = PublishingPlugin.PUBLISH_TASK_GROUP
            release.description = "Releases the Maven Central Portal publication"
            release.mustRunAfter(validate)
            release.mustRunAfter(zipMavenCentralPortal)
            release.ifThereAreInputFiles { portalDeployment.release() }
        }
        gradle.taskGraph.whenReady { taskGraph ->
            val allTasks = taskGraph.allTasks.map { it.name }.toSet()
            check(RELEASE_TASK_NAME !in allTasks || DROP_TASK_NAME !in allTasks) {
                "Task $RELEASE_TASK_NAME and $DROP_TASK_NAME cannot be executed together"
            }
        }
    }
}
