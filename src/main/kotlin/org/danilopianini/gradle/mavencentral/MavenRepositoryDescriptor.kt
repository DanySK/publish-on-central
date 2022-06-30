import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import java.time.Duration

/**
 * A descriptor of a Maven repository.
 * Requires a [name], and optionally authentication in form of [user] and [password].
 */
class MavenRepositoryDescriptor internal constructor(
    project: Project,
    var name: String,
) {
    /**
     * The username.
     */
    val user: Property<String> = project.objects.property()

    /**
     * The password.
     */
    val password: Property<String> = project.objects.property()

    /**
     * The Nexus URL, if installed.
     */
    var nexusUrl: String? = null

    /**
     * The Nexus timeout.
     */
    var nexusTimeOut: Duration = Duration.ofMinutes(1)

    /**
     * The Nexus connection timeout.
     */
    var nexusConnectionTimeout: Duration = nexusTimeOut
}
