import kotlin.String

/**
 * Find which updates are available by running
 *     `$ ./gradlew buildSrcVersions`
 * This will only update the comments.
 *
 * YOU are responsible for updating manually the dependency version. */
object Versions {
    const val com_gradle_plugin_publish_gradle_plugin: String = "0.10.1" 

    const val de_fayard_buildsrcversions_gradle_plugin: String = "0.3.2" 

    const val kotlintest_runner_junit5: String = "3.3.2" 

    const val org_danilopianini_git_sensitive_semantic_versioning_gradle_plugin: String = "0.2.2" 

    const val org_danilopianini_publish_on_central_gradle_plugin: String = "0.1.1" 

    const val org_jetbrains_dokka_gradle_plugin: String = "0.9.17" // available: "0.9.18"

    const val org_jetbrains_kotlin_jvm_gradle_plugin: String = "1.3.21" // available: "1.3.31"

    const val org_jetbrains_kotlin: String = "1.3.21" // available: "1.3.31"

    /**
     *
     *   To update Gradle, edit the wrapper file at path:
     *      ./gradle/wrapper/gradle-wrapper.properties
     */
    object Gradle {
        const val runningVersion: String = "5.2.1"

        const val currentVersion: String = "5.4.1"

        const val nightlyVersion: String = "5.6-20190530000040+0000"

        const val releaseCandidate: String = "5.5-rc-1"
    }
}
