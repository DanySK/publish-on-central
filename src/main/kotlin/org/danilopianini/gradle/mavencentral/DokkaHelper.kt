package org.danilopianini.gradle.mavencentral

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskCollection

/**
 * Looks for the class `DokkaPlugin`, which will only be available if the plugin user is applying the Dokka plugin.
 */
internal val dokkaPluginClass: Result<Class<Plugin<*>>> = runCatching {
    @Suppress("UNCHECKED_CAST")
    Class.forName("org.jetbrains.dokka.gradle.DokkaPlugin") as Class<Plugin<*>>
}

/**
 * Looks for the class `DokkaTask`, which will only be available if the plugin user is applying the Dokka plugin.
 */
internal val dokkaTaskClass: Result<Class<out Task>> = dokkaPluginClass.mapCatching {
    @Suppress("UNCHECKED_CAST")
    Class.forName("org.jetbrains.dokka.gradle.DokkaTask") as Class<out Task>
}

/**
 * Selects the available Dokka tasks supporting the generation of [docStyle]-style documentation.
 * There may be no such tasks, if the plugin user did not apply the Dokka plugin.
 */
internal fun Project.dokkaTasksFor(docStyle: Property<DocStyle>): TaskCollection<out Task> =
    dokkaTaskClass
        .map { dokkaTaskType ->
            tasks.withType(dokkaTaskType).matching {
                it.name.startsWith("dokka") && it.name.endsWith(docStyle.get().name, ignoreCase = true)
            }
        }
        .getOrElse { tasks.matching { false } }
