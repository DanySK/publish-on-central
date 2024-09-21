package org.danilopianini.gradle.mavencentral

/**
 * Admissible type of styles used by Dokka to generate documentation from KDoc.
 */
enum class DocStyle {
    /**
     * GitHub-Flavored Markdown.
     */
    GFM,

    /**
     * HTML.
     */
    HTML,

    /**
     * Classic Javadoc format.
     */
    JAVADOC,

    /**
     * Jekyll-rb.
     */
    JEKYLL,
}
