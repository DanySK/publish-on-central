package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection

/**
 * The full name of the `DokkaTask` class.
 */
private const val DOKKA_TASK_CLASS_NAME = "org.jetbrains.dokka.gradle.DokkaTask"

/**
 * The id of the Dokka plugin.
 */
internal const val DOKKA_PLUGIN_ID = "org.jetbrains.dokka"

/**
 * Checks whether a [Task] is actually an instance of the type `DokkaTask`, i.e., an instance of the type
 * named after [DOKKA_TASK_CLASS_NAME].
 */
internal val Task.isDokkaTask: Boolean
    get() = try {
        Class.forName(DOKKA_TASK_CLASS_NAME).isAssignableFrom(this::class.java)
    } catch (_: ClassNotFoundException) {
        this::class.java.name.startsWith(DOKKA_TASK_CLASS_NAME)
    }

/**
 * Selects the available Dokka tasks supporting the generation of [docStyle]-style documentation.
 * There may be no such tasks, if the plugin user did not apply the Dokka plugin.
 */
internal fun Project.dokkaTasksFor(docStyle: DocStyle): TaskCollection<out Task> =
    tasks.matching {
        it.isDokkaTask && it.name.startsWith("dokka") && it.name.endsWith(docStyle.name, ignoreCase = true)
    }

/**
 * If a task is of type `DokkaTask` (cf. [isDokkaTask]), then retrieves the value of its `outputDirectory`
 * property, if any.
 */
internal val Task.outputDirectory: Any
    get() = if (isDokkaTask) {
        property("outputDirectory")
            ?: error(
                "$name has no property 'outputDirectory' -" +
                    " maybe this version of Dokka is incompatible with publish-on-central?"
            )
    } else {
        error("$name is not of type $DOKKA_TASK_CLASS_NAME")
    }
