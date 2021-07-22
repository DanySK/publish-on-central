package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import java.net.URI

inline fun <reified T> Project.propertyWithDefault(default: T): Property<T> =
    objects.property(T::class.java).apply { convention(default) }

fun environmentVariable(name: String) =
    System.getenv(name)
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Environment variable $name is not available")

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

internal class PublishOnCentralConfiguration(project: Project) {
    val projectLongName: Property<String> = project.propertyWithDefault(project.name)

    val projectDescription: Property<String> = project.propertyWithDefault("No description provided")

    val licenseName: Property<String> = project.propertyWithDefault("Apache License, Version 2.0")

    val licenseUrl: Property<String> = project.propertyWithDefault("http://www.apache.org/licenses/LICENSE-2.0")

    val scmConnection: Property<String> = project.propertyWithDefault("git:git@github.com:DanySK/${project.name}")

    val projectUrl: Property<String> = project.propertyWithDefault("https://github.com/DanySK/${project.name}")
}

open class PublishOnCentralExtension(val project: Project) {

    internal val configuration = PublishOnCentralConfiguration(project)

    var projectLongName: String
        get() = configuration.projectLongName.get()
        set(value) = configuration.projectLongName.set(value)

    var projectDescription: String
        get() = configuration.projectDescription.get()
        set(value) = configuration.projectDescription.set(value)

    var licenseName: String
        get() = configuration.licenseName.get()
        set(value) = configuration.licenseName.set(value)

    var licenseUrl: String
        get() = configuration.licenseUrl.get()
        set(value) = configuration.licenseUrl.set(value)

    var scmConnection: String
        get() = configuration.scmConnection.get()
        set(value) = configuration.scmConnection.set(value)

    var projectUrl: String
        get() = configuration.projectUrl.get()
        set(value) = configuration.projectUrl.set(value)

    @JvmOverloads fun repository(
        url: String,
        name: String = extractName.find(url)?.destructured?.component1() ?: "unknown",
        configurator: MavenRepositoryDescriptor.() -> Unit = { }
    ) {
        val repoDescriptor = MavenRepositoryDescriptor(name, project).apply(configurator)
        Repository(name, url, { repoDescriptor.user }, { repoDescriptor.password })
            .configureForProject(project)
    }

    @JvmOverloads fun mavenCentralSnapshotsRepository(
        name: String = "MavenCentralSnapshots",
        configurator: MavenRepositoryDescriptor.() -> Unit = { },
    ) = repository(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/", name = name) {
        user = Repository.mavenCentral.user(project)
        password = Repository.mavenCentral.password(project)
        password = Repository.mavenCentral.password(project)
        apply(configurator)
    }

    fun MavenPublication.configurePomForMavenCentral() {
        pom { pom ->
            with(pom) {
                name.set(projectLongName)
                description.set(projectDescription)
                packaging = "jar"
                url.set(projectUrl)
                licenses {
                    it.license { license ->
                        license.name.set(licenseName)
                        license.url.set(licenseUrl)
                    }
                }
                scm { scm ->
                    scm.url.set(projectUrl)
                    scm.connection.set(scmConnection)
                    scm.developerConnection.set(scmConnection)
                }
            }
        }
    }

    companion object {
        val extractName = Regex(
            """.*://(?:\w+\.)*(\w+)\.\w+(?:/.*)?"""
        )
    }
}

data class MavenRepositoryDescriptor(
    var name: String,
    private val project: Project
) {
    var user: String? = null
    var password: String? = null
}
