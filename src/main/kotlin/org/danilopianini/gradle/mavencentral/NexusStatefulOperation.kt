package org.danilopianini.gradle.mavencentral

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import io.github.gradlenexus.publishplugin.internal.BasicActionRetrier
import io.github.gradlenexus.publishplugin.internal.NexusClient
import io.github.gradlenexus.publishplugin.internal.StagingRepositoryDescriptor
import io.github.gradlenexus.publishplugin.internal.StagingRepositoryTransitioner
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.net.URI
import java.time.Duration
import org.gradle.internal.impldep.com.google.api.client.http.HttpStatusCodes.STATUS_CODE_CREATED as HTTP_201_CREATED
import org.gradle.internal.impldep.com.google.api.client.http.HttpStatusCodes.STATUS_CODE_OK as HTTP_200_OK

/**
 * Lazy class acting as a container for stateful operations on Nexus.
 */
data class NexusStatefulOperation(
    private val project: Project,
    private val nexusUrl: String,
    private val user: Provider<String>,
    private val password: Provider<String>,
    private val timeOut: Duration,
    private val connectionTimeOut: Duration,
) {
    /**
     * Repository description.
     */
    val description by lazy { project.run { "$group:$name:$version" } }

    private val group: String by lazy {
        project.group.toString().apply {
            check(isNotBlank()) {
                "Project $project has no group set"
            }
        }
    }

    /**
     * The NexusClient.
     */
    val client: NexusClient by lazy {
        NexusClient(
            project.uri(nexusUrl),
            user.get(),
            password.get(),
            timeOut,
            connectionTimeOut,
        )
    }

    /**
     * Lazily computed staging profile id.
     */
    val stagingProfile: String by lazy {
        project.logger.lifecycle("Retrieving the profile id for $group on Nexus installed at $nexusUrl")
        requireNotNull(client.findStagingProfileId(group)) {
            "Invalid group id '$group': could not find an appropriate staging profile"
        }
    }

    /**
     * Lazily computed staging repository descriptor.
     */
    val stagingRepository: StagingRepositoryDescriptor by lazy {
        project.properties["stagingRepositoryId"]?.let {
            project.logger.lifecycle("Using existing staging repository {}", it)
            val stagingRepo = client.getStagingRepositoryStateById(it as String)
            return@lazy StagingRepositoryDescriptor(project.uri(nexusUrl), stagingRepo.id)
        } ?: run {
            project.logger.lifecycle("Creating repository for profile id {} on Nexus at {}", stagingProfile, nexusUrl)
            client.createStagingRepository(stagingProfile, description)
        }
    }

    /**
     * Lazily computed staging repository url.
     */
    val repoUrl: URI by lazy { stagingRepository.stagingRepositoryUrl }

    /**
     * Lazily computed staging repository id.
     */
    val repoId: String by lazy { stagingRepository.stagingRepositoryId }

    private val transitioner by lazy {
        StagingRepositoryTransitioner(
            client,
            BasicActionRetrier(Int.MAX_VALUE, Duration.ofSeconds(RETRY_INTERVAL)) { it.transitioning },
        )
    }

    /**
     * Closes the repository.
     */
    fun close() {
        project.logger.lifecycle("Closing repository {} on Nexus at {}", repoId, repoUrl)
        transitioner.effectivelyClose(repoId, description)
        project.logger.lifecycle("Repository $repoId closed")
    }

    /**
     * Releases the repository. Must be called after close().
     */
    fun release() {
        project.logger.lifecycle("Releasing repository {} on Nexus at {}", repoId, repoUrl)
        transitioner.effectivelyRelease(repoId, description)
        project.logger.lifecycle("Repository {} released", repoId)
    }

    /**
     * Drops the repository. Must be called after close().
     */
    fun drop() {
        project.logger.lifecycle("Dropping repository {} on Nexus at {}", repoId, repoUrl)
        runBlocking {
            Fuel
                .post("${nexusUrl.removeSuffix("/")}/staging/bulk/drop")
                .header(
                    "Accept" to "application/json",
                    "Content-Type" to "application/json",
                ).authentication()
                .basic(user.get(), password.get())
                .jsonBody("""{"data":{"stagedRepositoryIds":["$repoId"],"description":"$description"}}""")
                .response { _, response, _ ->
                    project.logger.lifecycle("Received response {} ", response)
                    check(response.statusCode in HTTP_200_OK..HTTP_201_CREATED) {
                        "Could not drop repository $repoId: HTTP ${response.statusCode} ${response.responseMessage}"
                    }
                }
        }
        project.logger.lifecycle("Requested drop for repository {} ", repoId)
    }

    private companion object {
        private const val RETRY_INTERVAL: Long = 10
    }
}
