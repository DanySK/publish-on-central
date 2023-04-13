package org.danilopianini.gradle.mavencentral

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.create
import kotlin.reflect.KClass

internal object ProjectExtensions {

    fun <T : Task> Project.registerTaskIfNeeded(
        name: String,
        type: KClass<T>,
        vararg parameters: Any = emptyArray(),
        configuration: T.() -> Unit = { },
    ): Task = tasks.findByName(name) ?: tasks.create(name, type, *parameters).apply(configuration)

    fun Project.registerTaskIfNeeded(
        name: String,
        vararg parameters: Any = emptyArray(),
        configuration: DefaultTask.() -> Unit = { },
    ): Task = registerTaskIfNeeded(
        name = name,
        type = DefaultTask::class,
        parameters = parameters,
        configuration = configuration,
    )
}
