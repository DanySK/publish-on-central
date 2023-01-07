package org.danilopianini.gradle.mavencentral

import io.github.gradlenexus.publishplugin.internal.BasicActionRetrier
import io.github.gradlenexus.publishplugin.internal.NexusClient
import io.github.gradlenexus.publishplugin.internal.StagingRepository
import io.github.gradlenexus.publishplugin.internal.StagingRepositoryDescriptor
import io.github.gradlenexus.publishplugin.internal.StagingRepositoryTransitioner
import khttp.structures.authorization.BasicAuthorization
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.net.URI
import java.time.Duration

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
    private val group: String,
) {

    /**
     * Repository description.
     */
    val description by lazy { project.run { "$group:$name:$version" } }

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
        project.logger.lifecycle("Creating repository for profile id {} on Nexus at {}", stagingProfile, nexusUrl)
        client.createStagingRepository(
            stagingProfile,
            description,
        )
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
            BasicActionRetrier(Int.MAX_VALUE, Duration.ofSeconds(retryInterval), StagingRepository::transitioning),
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
        khttp.post(
            url = "${nexusUrl.removeSuffix("/")}/staging/bulk/drop",
            auth = BasicAuthorization(user.get(), password.get()),
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
            ),
            data = """{"data":{"stagedRepositoryIds":["$repoId"],"description":"$description"}}""".trimIndent(),
        )
        project.logger.lifecycle("Requested drop for repository {} ", repoId)
    }

    companion object {
        private const val retryInterval: Long = 10
    }
}
