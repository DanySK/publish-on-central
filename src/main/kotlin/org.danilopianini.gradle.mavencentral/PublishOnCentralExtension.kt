package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPublication

inline fun <reified T> Project.propertyWithDefault(default: T): Property<T> =
    objects.property(T::class.java).apply { convention(default) }

internal class PublishOnCentralConfiguration(project: Project) {
    val projectLongName: Property<String> = project.propertyWithDefault(project.name)

    val projectDescription: Property<String> = project.propertyWithDefault("No description provided")

    val licenseName: Property<String> = project.propertyWithDefault("Apache License, Version 2.0")

    val licenseUrl: Property<String> = project.propertyWithDefault("http://www.apache.org/licenses/LICENSE-2.0")

    val scmConnection: Property<String> = project.propertyWithDefault("git:git@github.com:DanySK/${project.name}")

    val projectUrl: Property<String> = project.propertyWithDefault("https://github.com/DanySK/${project.name}")
}

/**
 * The extension in charge of configuring the publish-on-central plugin on the target [project].
 */
open class PublishOnCentralExtension(val project: Project) {

    internal val configuration = PublishOnCentralConfiguration(project)

    /**
     * The full project name.
     */
    var projectLongName: String
        get() = configuration.projectLongName.get()
        set(value) = configuration.projectLongName.set(value)

    /**
     * A description of the project.
     */
    var projectDescription: String
        get() = configuration.projectDescription.get()
        set(value) = configuration.projectDescription.set(value)

    /**
     * The project's license name.
     */
    var licenseName: String
        get() = configuration.licenseName.get()
        set(value) = configuration.licenseName.set(value)

    /**
     * The license URL connection of the project.
     */
    var licenseUrl: String
        get() = configuration.licenseUrl.get()
        set(value) = configuration.licenseUrl.set(value)

    /**
     * The SCM connection of the project.
     */
    var scmConnection: String
        get() = configuration.scmConnection.get()
        set(value) = configuration.scmConnection.set(value)

    /**
     * The URL of the project.
     */
    var projectUrl: String
        get() = configuration.projectUrl.get()
        set(value) = configuration.projectUrl.set(value)

    /**
     * Utility to configure a new Maven repository as target.
     */
    @JvmOverloads fun repository(
        url: String,
        name: String = repositoryNameFromURL(url),
        configurator: MavenRepositoryDescriptor.() -> Unit = { }
    ) {
        val repoDescriptor = MavenRepositoryDescriptor(name).apply(configurator)
        Repository(name, url, { repoDescriptor.user }, { repoDescriptor.password })
            .configureForProject(project)
    }

    /**
     * Utility to pre-configure a deployment towards the Maven Central Snapshots repository.
     */
    @JvmOverloads fun mavenCentralSnapshotsRepository(
        name: String = "MavenCentralSnapshots",
        configurator: MavenRepositoryDescriptor.() -> Unit = { },
    ) = repository(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/", name = name) {
        user = Repository.mavenCentral.user(project)
        password = Repository.mavenCentral.password(project)
        password = Repository.mavenCentral.password(project)
        apply(configurator)
    }

    /**
     * Configures the pom.xml file of a [MavenPublication] with the information specified in this configuration.
     */
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

        private val extractName = Regex(
            """.*://(?:\w+\.)*(\w+)\.\w+(?:/.*)?"""
        )

        private fun repositoryNameFromURL(url: String) = extractName.find(url)?.destructured?.component1() ?: "unknown"
    }
}

/**
 * A descriptor of a Maven repository.
 * Requires a [name], and optionally authentication in form of [user] and [password].
 */
class MavenRepositoryDescriptor internal constructor(
    var name: String,
) {
    /**
     * The username.
     */
    var user: String? = null

    /**
     * The password.
     */
    var password: String? = null
}
