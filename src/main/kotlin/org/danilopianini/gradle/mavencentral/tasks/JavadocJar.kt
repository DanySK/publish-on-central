package org.danilopianini.gradle.mavencentral.tasks

/**
 * A task generating a Jar file with the Javadoc.
 */
open class JavadocJar : JarWithClassifier("javadoc") {
    init {
        description = "Assembles a jar archive containing the javadoc documentation"
    }
}
