package org.danilopianini.gradle.mavencentral

import org.danilopianini.gradle.mavencentral.MavenConfigurationSupport.configureRepository
import org.danilopianini.gradle.mavencentral.ProjectExtensions.propertyWithDefault
import org.danilopianini.gradle.mavencentral.ProjectExtensions.propertyWithDefaultProvider
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property

/**
 * The extension in charge of configuring the publish-on-central plugin on the target [project].
 */
open class PublishOnCentralExtension(val project: Project) {
    /**
     * The full project name.
     */
    val projectLongName: Property<String> = project.propertyWithDefault(project.name)

    /**
     * A description of the project.
     */
    val projectDescription: Property<String> = project.objects.property<String>()

    /**
     * The project's license name.
     */
    val licenseName: Property<String> = project.propertyWithDefault("Apache License, Version 2.0")

    /**
     * The license URL connection of the project.
     */
    val licenseUrl: Property<String> = project.propertyWithDefault("http://www.apache.org/licenses/LICENSE-2.0")

    /**
     * For GitHub projects, the owner of the repo. Used for the default values of [projectUrl] and [scmConnection]
     */
    val repoOwner: Property<String> = project.objects.property<String>()

    /**
     * The SCM connection of the project.
     */
    val scmConnection: Property<String> =
        project.propertyWithDefaultProvider {
            "scm:git:https://github.com/${repoOwner.get()}/${project.name}"
        }

    /**
     * The URL of the project.
     */
    val projectUrl: Property<String> =
        project.propertyWithDefaultProvider {
            "https://github.com/${repoOwner.get()}/${project.name}"
        }

    /**
     * Utility to configure a new Maven repository as target.
     */
    @JvmOverloads fun repository(
        url: String,
        name: String = repositoryNameFromURL(url),
        configurator: Repository.() -> Unit = { },
    ) {
        val repo = Repository.fromProject(project, name, url)
        repo.apply(configurator)
        project.configureRepository(repo)
    }

    private companion object {
        private val extractName = Regex(""".*://(?:\w+\.)*(\w+)\.\w+(?:/.*)?""")

        private fun repositoryNameFromURL(url: String) = extractName.find(url)?.destructured?.component1() ?: "unknown"
    }
}
