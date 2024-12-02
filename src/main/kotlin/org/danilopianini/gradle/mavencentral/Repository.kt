package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import java.time.Duration

/**
 * A class modelling the concept of target Maven repository.
 * Includes a [name], an [url], and methods to compute [user] and [password] given a [Project].
 * If the repository is managed with Sonatype Nexus,
 * then the Nexus uri should be provided as [nexusUrl].
 * Time outs can be set with [nexusTimeOut] and [nexusConnectTimeOut].
 */
data class Repository(
    var name: String,
    val url: Property<String>,
    val user: Property<String>,
    val password: Property<String>,
    val nexusUrl: String? = null,
    val nexusTimeOut: Duration = Duration.ofMinutes(1),
    val nexusConnectTimeOut: Duration = Duration.ofMinutes(1),
) {
    /**
     * Same as [name], but capitalized.
     */
    val capitalizedName = name.replaceFirstChar(Char::titlecase)

    override fun toString() = "$name at $url"

    /**
     * Constants and utility functions.
     */
    companion object {
        /**
         * The default name of the Maven Central repository.
         */
        const val MAVEN_CENTRAL_NAME = "MavenCentral"

        /**
         * The default URL of Maven Central.
         */
        const val MAVEN_CENTRAL_URL = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"

        /**
         * The Sonatype Nexus instance URL of Maven Central.
         */
        const val MAVEN_CENTRAL_NEXUS_URL = "https://s01.oss.sonatype.org/service/local/"

        /**
         * Creates a named [Repository] from a [project] and a [name].
         */
        fun fromProject(
            project: Project,
            name: String,
        ): Repository =
            Repository(
                name = name,
                url = project.objects.property(),
                user = project.objects.property(),
                password = project.objects.property(),
            )
    }
}
