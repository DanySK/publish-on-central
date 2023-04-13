package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.register

internal object ProjectExtensions {

    inline fun <reified T> Project.createExtension(name: String, vararg args: Any?): T =
        project.extensions.create(name, T::class.java, *args)

    inline fun <reified T : Task> Project.registerTaskIfNeeded(
        name: String,
        vararg parameters: Any = emptyArray(),
        noinline configuration: Task.() -> Unit = { }
    ): Task =
        tasks.findByName(name) ?: tasks.register<T>(name, *parameters).get().apply(configuration)
}
