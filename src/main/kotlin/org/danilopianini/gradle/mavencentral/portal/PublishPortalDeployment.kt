package org.danilopianini.gradle.mavencentral.portal

import io.ktor.client.request.forms.InputProvider
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.streams.asInput
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.danilopianini.centralpublisher.api.PublishingApi
import org.danilopianini.centralpublisher.api.apiV1PublisherUploadPost
import org.danilopianini.centralpublisher.impl.infrastructure.HttpResponse
import org.danilopianini.centralpublisher.impl.models.DeploymentResponseFiles
import org.danilopianini.centralpublisher.impl.models.DeploymentResponseFiles.DeploymentState.FAILED
import org.danilopianini.centralpublisher.impl.models.DeploymentResponseFiles.DeploymentState.PENDING
import org.danilopianini.centralpublisher.impl.models.DeploymentResponseFiles.DeploymentState.PUBLISHED
import org.danilopianini.centralpublisher.impl.models.DeploymentResponseFiles.DeploymentState.PUBLISHING
import org.danilopianini.centralpublisher.impl.models.DeploymentResponseFiles.DeploymentState.VALIDATED
import org.danilopianini.centralpublisher.impl.models.DeploymentResponseFiles.DeploymentState.VALIDATING
import org.gradle.api.Project
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Lazy class acting as a container for stateful operations on Maven Central Portal.
 */
data class PublishPortalDeployment(
    private val project: Project,
    private val baseUrl: String,
    private val user: Provider<String>,
    private val password: Provider<String>,
    private val zipTask: TaskProvider<out ConventionTask>,
) {
    /**
     * The Publishing portal client.
     */
    val client: PublishingApi by lazy {
        PublishingApi(baseUrl).apply {
            check(user.isPresent) {
                "Username for the central portal at $baseUrl is not set."
            }
            check(password.isPresent) {
                "Password for the central portal at $baseUrl is not set."
            }
            setUsername(user.get())
            setPassword(password.get())
        }
    }

    /**
     * THe zip file to upload.
     */
    val fileToUpload: File by lazy {
        zipTask.get().outputs.files.singleFile.apply {
            check(exists() && isFile) {
                "File $absolutePath does not exist or is not a file, did task ${zipTask.name} run?"
            }
        }
    }

    /**
     * Uploads a bundle to the Central Portal, returning the upload id.
     */
    @JvmOverloads
    suspend fun upload(bundle: File, name: String = bundle.name, releaseAfterUpload: Boolean = false): String {
        val response = client.apiV1PublisherUploadPost(
            name,
            releaseAfterUpload = releaseAfterUpload,
            bundle = InputProvider(bundle.length()) { bundle.inputStream().asInput() },
        )
        return when (response.status) {
            OK, CREATED -> {
                project.logger.lifecycle("Bundle from file ${bundle.path} uploaded successfully")
                response.body()
            }
            INTERNAL_SERVER_ERROR -> error("Error on bundle upload")
            else -> maybeUnauthorized("upload", response)
        }
    }

    /**
     * Lazily computed staging repository descriptor.
     */
    val deploymentId: String by lazy {
        when (val idFromProperty = project.properties[PUBLISH_DEPLOYMENT_ID_PROPERTY_NAME]) {
            null -> {
                runBlocking { upload(fileToUpload) }
            }
            else ->
                idFromProperty.toString().also {
                    project.logger.lifecycle("Using existing deployment id {}", it)
                }
        }
    }

    private suspend fun deploymentStatus(): DeploymentResponseFiles {
        val response = client.apiV1PublisherStatusPost(deploymentId)
        val body =
            when (response.status) {
                OK -> response.typedBody<DeploymentResponseFiles>(typeInfo<DeploymentResponseFiles>())
                INTERNAL_SERVER_ERROR -> error("Error on deployment $deploymentId status query")
                else -> maybeUnauthorized("deployment status check", response)
            }
        return body
    }

    /**
     * Validates the deployment.
     */
    tailrec suspend fun validate(waitAmongRetries: Duration = waitingTime) {
        project.logger.lifecycle("Validating deployment {} on Central Portal at {}", deploymentId, baseUrl)
        val responseBody = deploymentStatus()
        when (responseBody.deploymentState) {
            PENDING, VALIDATING -> {
                delay(waitAmongRetries)
                validate(waitAmongRetries * 2)
            }
            VALIDATED, PUBLISHING, PUBLISHED -> project.logger.lifecycle("Deployment {} validated", deploymentId)
            FAILED -> error("Deployment $deploymentId validation FAILED")
            null -> error("Unexpected/unknown deployment state null for deployment $deploymentId")
        }
    }

    /**
     * Releases the deployment.
     */
    tailrec suspend fun release(waitAmongRetries: Duration = waitingTime): Unit =
        when (deploymentStatus().deploymentState) {
            null -> error("Unexpected/unknown deployment state null for deployment $deploymentId")
            PENDING, VALIDATING, PUBLISHING -> {
                delay(waitAmongRetries)
                release(waitAmongRetries * 2)
            }
            PUBLISHED ->
                project.logger.lifecycle("Deployment {} has been already released", deploymentId)
            VALIDATED -> {
                project.logger.lifecycle("Releasing deployment {}", deploymentId)
                val releaseResponse = client.apiV1PublisherDeploymentDeploymentIdPost(deploymentId)
                when (releaseResponse.status) {
                    NO_CONTENT -> project.logger.lifecycle("Deployment {} released", deploymentId)
                    NOT_FOUND -> error("Deployment $deploymentId not found. $releaseResponse")
                    INTERNAL_SERVER_ERROR ->
                        error("Internal server error when releasing $deploymentId: $releaseResponse")
                    else -> maybeUnauthorized("deployment release", releaseResponse)
                }
            }
            FAILED -> error("Deployment $deploymentId validation FAILED")
        }

    /**
     * Drops the repository. Must be called after close().
     */
    tailrec suspend fun drop(waitAmongRetries: Duration = waitingTime): Unit =
        when (deploymentStatus().deploymentState) {
            null -> error("Unexpected/unknown deployment state null for deployment $deploymentId")
            PENDING, VALIDATING, PUBLISHING -> {
                delay(waitAmongRetries)
                drop(waitAmongRetries * 2)
            }
            PUBLISHED ->
                error("Deployment $deploymentId has been published already and cannot get dropped")
            FAILED, VALIDATED -> {
                project.logger.lifecycle("Dropping deployment {}", deploymentId)
                val releaseResponse = client.apiV1PublisherDeploymentDeploymentIdDelete(deploymentId)
                when (releaseResponse.status) {
                    NO_CONTENT -> project.logger.lifecycle("Deployment {} dropped", deploymentId)
                    NOT_FOUND -> error("Deployment $deploymentId not found. $releaseResponse")
                    INTERNAL_SERVER_ERROR ->
                        error("Internal server error when dropping $deploymentId: $releaseResponse")
                    else -> maybeUnauthorized("deployment release", releaseResponse)
                }
            }
        }

    /**
     * Constants for the Central Portal Deployments.
     */
    companion object {
        /**
         * The property name for the deployment id.
         */
        const val PUBLISH_DEPLOYMENT_ID_PROPERTY_NAME = "publishDeploymentId"

        /**
         * The bundle validation task name.
         */
        const val VALIDATE_TASK_NAME = "validateMavenCentralPortalPublication"

        /**
         * The bundle drop task name.
         */
        const val DROP_TASK_NAME = "dropMavenCentralPortalPublication"

        /**
         * The bundle release task name.
         */
        const val RELEASE_TASK_NAME = "releaseMavenCentralPortalPublication"
        private const val OK = 200
        private const val CREATED = 201
        private const val NO_CONTENT = 204
        private const val BAD_REQUEST = 400
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private const val INTERNAL_SERVER_ERROR = 500

        private val waitingTime: Duration = 1.seconds

        private fun maybeUnauthorized(action: String, response: HttpResponse<*>): Nothing = when (response.status) {
            BAD_REQUEST -> error("Authentication failure, make sure that your credentials are correct")
            UNAUTHORIZED -> error("No active session or not authenticated, check your credentials")
            FORBIDDEN -> error("User unauthorized to perform the $action action")
            else -> error("Unexpected response $response")
        }
    }
}
