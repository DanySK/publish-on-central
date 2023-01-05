package org.danilopianini.gradle.mavencentral

import MavenRepositoryDescriptor
import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.time.Duration

/**
 * The extension in charge of configuring the publish-on-central plugin on the target [project].
 */
open class PublishOnCentralExtension(val project: Project) {

    /**
     * Whether the plugin should consider all MavenPublications as potentially deliverable on Maven Central,
     * and should thus configure them appropriately.
     * If disabled, the publications to be sent on Central must be configured manually by calling
     * [configureForMavenCentral] on them in the buildscript.
     */
    val autoConfigureAllPublications: Property<Boolean> = project.propertyWithDefault(true)

    /**
     * Easier access to the default Maven Central configuration.
     */
    val mavenCentral: Repository = Repository(
        Repository.mavenCentralName,
        url = Repository.mavenCentralURL,
        user = project.propertyWithDefaultProvider {
            System.getenv("MAVEN_CENTRAL_USERNAME")
                ?: project.properties["mavenCentralUsername"]?.toString()
                ?: project.properties["sonatypeUsername"]?.toString()
                ?: project.properties["ossrhUsername"]?.toString()
        },
        password = project.propertyWithDefaultProvider {
            System.getenv("MAVEN_CENTRAL_PASSWORD")
                ?: project.properties["mavenCentralPassword"]?.toString()
                ?: project.properties["sonatypePassword"]?.toString()
                ?: project.properties["ossrhPassword"]?.toString()
        },
        nexusUrl = Repository.mavenCentralNexusUrl,
        nexusTimeOut = @Suppress("MagicNumber") Duration.ofMinutes(5),
        nexusConnectTimeOut = Duration.ofMinutes(3),
    )

    /**
     * The full project name.
     */
    val projectLongName: Property<String> = project.propertyWithDefault(project.name)

    /**
     * A property, defaulting to true, that is used to disable the default configuration for Maven Central.
     * To be used in case of deployment towards only targets other than Maven Central.
     */
    val configureMavenCentral: Property<Boolean> = project.propertyWithDefault(true)

    /**
     * A description of the project.
     */
    var projectDescription: Property<String> = project.propertyWithDefault("No description provided")

    /**
     * The project's license name.
     */
    var licenseName: Property<String> = project.propertyWithDefault("Apache License, Version 2.0")

    /**
     * The license URL connection of the project.
     */
    var licenseUrl: Property<String> = project.propertyWithDefault("http://www.apache.org/licenses/LICENSE-2.0")

    /**
     * The SCM connection of the project.
     */
    var scmConnection: Property<String> = project.propertyWithDefault(
        "scm:git:https://github.com/DanySK/${project.name}"
    )

    /**
     * The URL of the project.
     */
    var projectUrl: Property<String> = project.propertyWithDefault("https://github.com/DanySK/${project.name}")

    /**
     * Utility to configure a new Maven repository as target.
     */
    @JvmOverloads fun repository(
        url: String,
        name: String = repositoryNameFromURL(url),
        configurator: MavenRepositoryDescriptor.() -> Unit = { }
    ) {
        val repoDescriptor = MavenRepositoryDescriptor(project, name).apply(configurator)
        val repo = Repository(
            repoDescriptor.name,
            url,
            repoDescriptor.user,
            repoDescriptor.password,
            repoDescriptor.nexusUrl,
            repoDescriptor.nexusTimeOut,
            repoDescriptor.nexusConnectionTimeout,
        )
        project.afterEvaluate { it.configureRepository(repo) }
    }

    /**
     * Utility to pre-configure a deployment towards the Maven Central Snapshots repository.
     */
    @JvmOverloads fun mavenCentralSnapshotsRepository(
        name: String = "MavenCentralSnapshots",
        configurator: MavenRepositoryDescriptor.() -> Unit = { },
    ) = repository(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/", name = name) {
        user.set(mavenCentral.user)
        password.set(mavenCentral.password)
        apply(configurator)
    }

    companion object {

        private val extractName = Regex(
            """.*://(?:\w+\.)*(\w+)\.\w+(?:/.*)?"""
        )

        private fun repositoryNameFromURL(url: String) = extractName.find(url)?.destructured?.component1() ?: "unknown"
    }
}
