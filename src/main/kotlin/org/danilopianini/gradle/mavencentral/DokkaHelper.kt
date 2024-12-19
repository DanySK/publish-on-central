package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaTask

/**
 * The id of the Dokka plugin.
 */
internal const val DOKKA_PLUGIN_ID = "org.jetbrains.dokka"

/**
 * Checks whether a [Task] is actually an instance of the type [DokkaTask].
 */
internal val Task.isDokkaTask: Boolean get() = this is DokkaTask

/**
 * Selects the available Dokka tasks supporting the generation of [docStyle]-style documentation.
 * There may be no such tasks, if the plugin user did not apply the Dokka plugin.
 */
internal fun Project.dokkaTasksFor(docStyle: DocStyle): TaskCollection<out DokkaTask> =
    tasks
        .withType<DokkaTask>()
        .matching {
            it.name.startsWith("dokka") && it.name.endsWith(docStyle.name, ignoreCase = true)
        }
