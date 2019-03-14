package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.lang.IllegalStateException
import java.net.URI
import java.net.URL

private inline fun <reified T> Project.propertyWithDefault(default: T): Property<T> =
    objects.property(T::class.java).apply { convention(default) }

private inline fun <reified T> Project.propertyWithDefault(noinline default: () -> T): Property<T> =
    objects.property(T::class.java).apply { convention( default()) }

open class PublishOnCentralExtension @JvmOverloads constructor(
    private val project: Project,
    val artifactId: Property<String> = project.propertyWithDefault(project.name),
    val projectDescription: Property<String> = project.propertyWithDefault("No description provided"),
    val scmRootUrl: Property<URL> = project.propertyWithDefault(URL("https://github.com/DanySK")),
    val licenseName: Property<String> = project.propertyWithDefault("Apache License, Version 2.0"),
    val licenseUrl: Property<String> = project.propertyWithDefault("http://www.apache.org/licenses/LICENSE-2.0"),
    val scmType: Property<String> = project.propertyWithDefault("git"),
    val scmRepoName: Property<String> = project.propertyWithDefault(project.name),
    val scmLogin: Property<String> = project.propertyWithDefault("git@github.com:DanySK"),
    val signArchives: Property<Boolean> = project.propertyWithDefault(false),
    val repositoryURL: Property<URI> = project.propertyWithDefault(URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")),
    val ossrhUsername: Property<String> = project.propertyWithDefault("danysk"),
    val ossrhPassword: Property<String> = project.propertyWithDefault {
        if (project.hasProperty(pwdName)) {
            project.property(pwdName) as? String
                ?: throw IllegalStateException("${pwdName} is set but it's not a String: ${project.property(pwdName)}")
        } else {
            throw IllegalStateException("""Property '${pwdName}' must be set as a project property, or configured:
                |publishOnCentral {
                |    ossrhPassword.set(<some function that retrieves your password>())
                |}""".trimMargin())
        }
    }
) {
    companion object {
        private const val pwdName = "ossrhPassword"
        const val extensionName = "publishOnCentral"
    }
}