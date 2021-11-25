package org.danilopianini.gradle.mavencentral

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import java.io.File

/**
 * A [Jar] task with the specified classifier, and adopting the duplicate strategy
 * [org.gradle.api.file.DuplicatesStrategy.WARN].
 */
open class JarWithClassifier(classifier: String) : Jar() {
    init {
        archiveClassifier.set(classifier)
        duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.WARN
    }
}

/**
 * A task generating a Jar file with the Javadoc.
 */
open class JavadocJar : JarWithClassifier("javadoc")

/**
 * A task generating a Jar file with the project source code.
 */
open class JarTasks : JarWithClassifier("sources") {
    init {
        sourceSet("main", false)
    }

    /**
     * Adds the [SourceSet] with the provided [name] to the contents of the [JarTasks].
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
