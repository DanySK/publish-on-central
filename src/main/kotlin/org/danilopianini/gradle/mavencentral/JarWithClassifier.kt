package org.danilopianini.gradle.mavencentral

import org.gradle.jvm.tasks.Jar

/**
 * A [Jar] task with the specified classifier, and adopting the duplicate strategy
 * [org.gradle.api.file.DuplicatesStrategy.WARN].
 */
open class JarWithClassifier(classifier: String) : Jar() {
    init {
        archiveClassifier.set(classifier)
        duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.WARN
        group = "Build"
    }
}
