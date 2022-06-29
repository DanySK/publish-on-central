package org.danilopianini.gradle.mavencentral

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import java.io.File

/**
 * A task generating a Jar file with the project source code.
 */
open class SourceJar : JarWithClassifier("sources") {
    init {
        description = "Assembles a jar archive containing the sources"
        sourceSet("main", false)
    }

    /**
     * Adds the [SourceSet] with the provided [name] to the contents of the [SourceJar].
     * In case the source set does not exist, if [failOnMissingName] is set, the task throws [IllegalStateException].
     */
    @JvmOverloads
    fun sourceSet(name: String, failOnMissingName: Boolean = true) {
        val sourceSets = project.properties["sourceSets"] as? SourceSetContainer
        if (sourceSets == null && failOnMissingName) {
            throw IllegalStateException("Project has no property 'sourceSets' of type 'SourceSetContainer'")
        }
        val sourceSet = sourceSets?.getByName(name)
        if (sourceSet != null) {
            sourceSet(sourceSet)
        } else if (failOnMissingName) {
            throw IllegalStateException("Project has no source set named $name")
        }
    }

    /**
     * Adds a [sourceSet] source.
     */
    fun sourceSet(sourceSet: SourceSet) {
        sourceSet(sourceSet.allSource)
    }

    /**
     * Adds a [sourceDirectorySet] source.
     */
    fun sourceSet(sourceDirectorySet: SourceDirectorySet) {
        from(sourceDirectorySet)
    }

    /**
     * Adds a [file] source.
     */
    fun source(file: File) {
        from(file)
    }
}
