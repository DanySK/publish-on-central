package org.danilopianini.gradle.mavencentral.tasks

import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.work.DisableCachingByDefault

/**
 * A [Jar] task with the specified classifier, and adopting the duplicate strategy
 * [org.gradle.api.file.DuplicatesStrategy.WARN].
 */
@DisableCachingByDefault(because = "Caching behavior has not been reviewed for this custom Jar task yet")
abstract class JarWithClassifier(classifier: String) : Jar() {
    init {
        archiveClassifier.set(classifier)
        duplicatesStrategy = DuplicatesStrategy.WARN
        group = "Build"
    }
}
