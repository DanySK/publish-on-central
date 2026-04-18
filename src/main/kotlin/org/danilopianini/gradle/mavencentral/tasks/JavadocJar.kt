package org.danilopianini.gradle.mavencentral.tasks

import org.gradle.work.DisableCachingByDefault

/**
 * A task generating a Jar file with the Javadoc.
 */
@DisableCachingByDefault(because = "Caching behavior depends on documentation task inputs configured at runtime")
abstract class JavadocJar : JarWithClassifier("javadoc") {
    init {
        description = "Assembles a jar archive containing the javadoc documentation"
    }
}
