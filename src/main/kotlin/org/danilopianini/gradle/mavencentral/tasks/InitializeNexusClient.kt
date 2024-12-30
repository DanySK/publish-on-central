package org.danilopianini.gradle.mavencentral.tasks

import javax.inject.Inject
import org.danilopianini.gradle.mavencentral.Repository
import org.danilopianini.gradle.mavencentral.nexus.NexusStatefulOperation
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * A task that creates a Nexus Client operating at the [Repository] [repoToConfigure] with URL [nexusUrl].
 */
open class InitializeNexusClient
    @Inject
    constructor(
        @Input
        val repoToConfigure: Repository,
        @Input
        val nexusUrl: String,
    ) : DefaultTask() {
        /**
         * The Nexus Client, accessible only **after** the execution of the task.
         */
        @Internal
        lateinit var nexusClient: NexusStatefulOperation

        /**
         * Initializes the Nexus Client.
         */
        @TaskAction
        fun initializeClient() {
            nexusClient =
                NexusStatefulOperation(
                    project = project,
                    nexusUrl = nexusUrl,
                    user = repoToConfigure.user,
                    password = repoToConfigure.password,
                    timeOut = repoToConfigure.nexusTimeOut,
                    connectionTimeOut = repoToConfigure.nexusConnectTimeOut,
                )
        }
    }
