package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

/**
 * Extension functions for org.gradle.api.publish.maven.MavenPublication to target Maven Central.
 */
object MavenPublicationExtensions {
    /**
     * Configures a [MavenPublication] for publication on Maven Central, adding the following.
     * - appropriate pom.xml configuration
     * - a main jar file
     * - a source jar file
     * - a javadoc jar file
     */
    fun MavenPublication.configureForMavenCentral(extension: PublishOnCentralExtension) {
        configurePomForMavenCentral(extension)
        val project = extension.project
        // Signing
        if (signingTasks(project).isEmpty()) {
            project.configure<SigningExtension> {
                sign(this@configureForMavenCentral)
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
     * Returns the signing tasks registered for the [MavenPublication] in the current [project].
     */
    fun MavenPublication.signingTasks(project: Project): Collection<Sign> =
        project.tasks.withType<Sign>().matching { signingTask ->
            signingTask.name.endsWith("sign${name.replaceFirstChar { it.uppercaseChar() }}Publication")
        }
}
