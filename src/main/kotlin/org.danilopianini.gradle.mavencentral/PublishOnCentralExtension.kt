package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.lang.IllegalStateException
import java.net.URI
import java.net.URL

inline fun <reified T> Project.propertyWithDefault(default: T): Property<T> =
    objects.property(T::class.java).apply { convention(default) }

inline fun <reified T> Project.propertyWithDefault(noinline default: () -> T): Property<T> =
    objects.property(T::class.java).apply { convention( default()) }

open class PublishOnCentralExtension @JvmOverloads constructor(
    private val project: Project,
    val projectLongName: Property<String> = project.propertyWithDefault(project.name),
    val projectDescription: Property<String> = project.propertyWithDefault("No description provided"),
    val licenseName: Property<String> = project.propertyWithDefault("Apache License, Version 2.0"),
    val licenseUrl: Property<String> = project.propertyWithDefault("http://www.apache.org/licenses/LICENSE-2.0"),
    val signArchives: Property<Boolean> = project.propertyWithDefault(false),
    val scmConnection: Property<String> = project.propertyWithDefault("git:git@github.com:DanySK/${project.name}"),
    val projectUrl: Property<String> = project.propertyWithDefault("https://github.com/DanySK/${project.name}"),
    val repositoryURL: Property<URI> = project.propertyWithDefault(URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/"))
) {
    companion object {
        const val userName = "ossrhUsername"
        const val pwdName = "ossrhPassword"
        const val extensionName = "publishOnCentral"
    }
}