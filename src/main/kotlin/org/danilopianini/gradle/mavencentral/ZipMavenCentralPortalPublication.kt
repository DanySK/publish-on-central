package org.danilopianini.gradle.mavencentral

import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject

/**
 * A Zip task that creates a zip file containing the local Maven repository.
 */
open class ZipMavenCentralPortalPublication
    @Inject
    constructor() : Zip() {
        init {
            group = PublishingPlugin.PUBLISH_TASK_GROUP
            description = "Creates a zip file containing the project-local Maven repository"
            from(Repository.projectLocalRepository(project).url)
            archiveBaseName.set(project.name + "-maven-central-portal")
            destinationDirectory.set(project.layout.buildDirectory.dir("maven-central-portal"))
            mustRunAfter(project.tasks.withType<PublishToMavenRepository>())
        }
    }
