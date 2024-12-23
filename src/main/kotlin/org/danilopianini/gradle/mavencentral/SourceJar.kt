package org.danilopianini.gradle.mavencentral

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension
import java.io.File

/**
 * A task generating a Jar file with the project source code.
 */
open class SourceJar : JarWithClassifier("sources") {
    init {
        description = "Assembles a jar archive containing the sources"
        sourceSet("main", false)
        project.pluginManager.withPlugin(PublishOnCentral.KOTLIN_JS_PLUGIN) { _ ->
            println("KOTLIN JS APPLIED")
            project.extensions.configure<KotlinJsProjectExtension> {
                println("KOTLIN JS EXTENSION")
                val jsMainSourceSets = sourceSets.getByName("main")
                sourceSet(jsMainSourceSets.kotlin)
                sourceSet(jsMainSourceSets.resources)
            }
        }
    }

    /**
     * Adds the [SourceSet] with the provided [name] to the contents of the [SourceJar].
     * In case the source set does not exist, if [failOnMissingName] is set, the task throws [IllegalStateException].
     */
    @JvmOverloads
    fun sourceSet(
        name: String,
        failOnMissingName: Boolean = true,
    ) {
        val sourceSets = project.properties["sourceSets"] as? SourceSetContainer
        if (sourceSets != null) {
            val sourceSet = sourceSets.findByName(name)
            if (sourceSet != null) {
                sourceSet(sourceSet)
            } else {
                check(!failOnMissingName) {
                    "Project has property 'sourceSets' of type 'SourceSetContainer', but it contains no '$name' entry"
                }
            }
        } else {
            check(!failOnMissingName) {
                "Project has no property 'sourceSets' of type 'SourceSetContainer'"
            }
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
