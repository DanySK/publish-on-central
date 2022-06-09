package org.danilopianini.gradle.mavencentral

import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import java.net.URI

internal inline fun <reified T> Project.propertyWithDefault(default: T?): Property<T> =
    objects.property(T::class.java).apply { convention(default) }

internal inline fun <reified T> Project.propertyWithDefaultProvider(noinline default: () -> T?): Property<T> =
    objects.property(T::class.java).apply { convention(provider(default)) }

/**
 * Configures a [MavenPublication] for publication on Maven Central, adding the following.
 * - appropriate pom.xml configuration
 * - a main jar file
 * - a source jar file
 * - a javadoc jar file
 */
fun MavenPublication.configureForMavenCentral(extension: PublishOnCentralExtension) {
    val project = extension.project
    configurePomForMavenCentral(extension)
    if (pom.packaging == "jar") {
        val jarTasks = project.tasks.withType<Jar>()
        // Required artifacts
        if (artifacts.none { it.extension == "jar" && it.classifier.isNullOrEmpty() }) {
            project.logger.debug("Publication '{}' has no pre-configured classifier-less jar", name)
            artifact(jarTasks.findJarTaskWithClassifier("", "jar", this))
        }
        if (artifacts.none { it.classifier == "sources" }) {
            project.logger.debug("Publication '{}' has no pre-configured source jar", name)
            artifact(jarTasks.findJarTaskWithClassifier("sources", "sourcesJar", this))
        }
        if (artifacts.none { it.classifier == "javadoc" }) {
            project.logger.debug("Publication '{}' has no pre-configured javadoc jar", name)
            artifact(jarTasks.findJarTaskWithClassifier("javadoc", "javadocJar", this))
        }
    }
    // Signing
    project.configure<SigningExtension> {
        sign(this@configureForMavenCentral)
    }
}

private fun DomainObjectCollection<Jar>.findJarTaskWithClassifier(
    classifier: String,
    preferredName: String,
    publication: MavenPublication,
): Jar {
    val withClassifier = filter {
        with(it.archiveClassifier.orNull) {
            if (classifier.isEmpty()) isNullOrEmpty() else this == classifier
        }
    }
    fun instructions() = """
        |You can either:
        |    - create a task that generates a jar without the correct classifier for publish-on-central to bind to, or
        |    - bind yourself a jar with the right classifier as artifact for the publication, or
        |    - disable the automatic configuration of all Maven publications for Maven Central with:
        |        publishOnCentral {
        |            autoConfigureAllPublications.set(false)
        |        }
        |      then manually configure the publications you want on Central with
        |        publishing.publications.withType<MavenPublication>/* filter the ones you want*/ {
        |            configureForMavenCentral(publishOnCentral)
        |        }
    """.trimMargin()
    check(withClassifier.isNotEmpty()) {
        """
        |Publication '${
        publication.name
        }' with packaging type '${
        publication.pom.packaging
        }' has no jar with ${
        if (classifier.isEmpty()) "no classifier" else "classifier '$classifier'"
        }, which is required for Maven Central.
        |${instructions()}
        """.trimMargin()
    }
    return when (withClassifier.size) {
        1 -> withClassifier.first()
        else -> {
            val best = withClassifier.find { it.name == preferredName }
            check(best != null) {
                """
                |Publication $publication needs a jar with classifier '$classifier', and these tasks are available:
                |${withClassifier.map { it.name }}
                |Publish-on-central tried to find one named '$preferredName' among them, but there was none.
                |${instructions()}
                """.trimMargin()
            }
            best
        }
    }
}

/**
 * Configures the pom.xml file of a [MavenPublication] with the information specified in this configuration.
 */
fun MavenPublication.configurePomForMavenCentral(extension: PublishOnCentralExtension) {
    pom { pom ->
        with(pom) {
            name.set(extension.projectLongName)
            description.set(extension.projectDescription)
            packaging = "jar"
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
                mavenArtifactRepository.url = URI(repoToConfigure.url)
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
    the<PublishingExtension>().publications.withType<MavenPublication>().forEach { publication ->
        val nexus = NexusStatefulOperation(
            project = project,
            nexusUrl = nexusUrl,
            group = project.group.toString(),
            user = repoToConfigure.user,
            password = repoToConfigure.password,
            timeOut = repoToConfigure.nexusTimeOut,
            connectionTimeOut = repoToConfigure.nexusConnectTimeOut,
        )
        val publicationName = publication.name.replaceFirstChar(Char::titlecase)
        val uploadArtifacts = project.tasks.create(
            "upload${publicationName}To${repoToConfigure.name}Nexus",
            PublishToMavenRepository::class,
        ) { publishTask ->
            publishTask.repository = project.repositories.maven { repo ->
                repo.name = repoToConfigure.name
                repo.url = project.uri(repoToConfigure.url)
                repo.credentials {
                    it.username = repoToConfigure.user.orNull
                    it.password = repoToConfigure.password.orNull
                }
            }
            publishTask.doFirst {
                warnIfCredentialsAreMissing(repoToConfigure)
                publishTask.repository.url = nexus.repoUrl
            }
            publishTask.publication = publication
            publishTask.group = PublishingPlugin.PUBLISH_TASK_GROUP
            publishTask.description = "Initializes a new Nexus repository on ${repoToConfigure.name} " +
                "and uploads the $publicationName publication."
        }
        val closeRepository = tasks.create("close${publicationName}On${repoToConfigure.name}Nexus") {
            it.doLast { nexus.close() }
            it.dependsOn(uploadArtifacts)
            it.group = PublishingPlugin.PUBLISH_TASK_GROUP
            it.description = "Closes the Nexus repository on ${repoToConfigure.name} with the " +
                "$publicationName publication."
        }
        tasks.create("release${publicationName}On${repoToConfigure.name}Nexus") {
            it.doLast { nexus.release() }
            it.dependsOn(closeRepository)
            it.group = PublishingPlugin.PUBLISH_TASK_GROUP
            it.description = "Releases the Nexus repo on ${repoToConfigure.name} " +
                "with the $publicationName publication."
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
