# maven-central-gradle-plugin
A Gradle plugin for streamlined publishing on Maven Central

## Rationale
Publishing on Maven Central requires too much configuration?
Well, I agree.
This plugin is here to simplify your life by automatically configuring (when applied) the Java plugin and the Publish
Plugin to create source and javadoc jars, sign them for you and send them to OSSRH's Sonatype Nexus.

## Usage

### Provided tasks

* `sourcesJar`: a `Jar` task preconfigured to collect and pack `allSource` from the `main` source set
* `javadocJar`: a `Jar` task preconfigured to
    1. Detect if a javadoc tasks exists, and in case depend on it, and pack its output folder
    2. Detect if a dokkaJavadoc tasks exists, and to the same as above
* One task for each combination of `SoftwareComponent` and repositories.
  The `MavenCentral` and `MavenCentralSnapshots` are predefined, other repositories can be added.
* One task for publishing `All` software components to any target repository

In short, if you have (for instance) a mixed Java-Kotlin project,
you should find the following tasks:

* `publishJavaMavenPublicationToMavenCentralRepository`
* `publishKotlinMavenPublicationToMavenCentralRepository`
* `publishAllPublicationsToMavenCentralRepository`
* `publishJavaMavenPublicationToMavenLocalRepository`
* `publishKotlinMavenPublicationToMavenLocalRepository`
* `publishAllPublicationsToMavenLocalRepository`

If you add a custom repository, say `myRepo`, you would also find the following tasks:

* `publishJavaMavenPublicationToMyRepoRepository`
* `publishKotlinMavenPublicationToMyRepoRepository`
* `publishAllPublicationsToMyRepoRepository`

which is what needs to get called to have your artifacts uploaded on OSSRH Nexus instance.

### Importing the plugin

```kotlin
plugins {
    id ("org.danilopianini.publish-on-central") version "0.5.1"
}
```
The plugin is configured to react to the application of the `java` plugin, and to apply the `maven-publish` and `signing` plugin if they are not applied.

### Configuring the plugin

```kotlin
group = "your.group.id" // This must be configured for the generated pom.xml to work correctly
/*
 * The plugin comes with defaults that are useful to myself. You should configure it to behave as you please:
 */
publishOnCentral {
    // The following values are the default, if they are ok with you, just omit them
    projectDescription = "No description provided"
    projectLongName = project.name
    licenseName = "Apache License, Version 2.0"
    licenseUrl = "http://www.apache.org/licenses/LICENSE-2.0"
    projectUrl = "https://github.com/DanySK/${project.name}"
    scmConnection = "git:git@github.com:DanySK/${project.name}"
    /*
     * The plugin is pre-configured to fetch credentials for Maven Central from the environment
     * Username from: MAVEN_CENTRAL_USERNAME
     * Password from: MAVEN_CENTRAL_PASSWORD
     *
     * In case of failure, it falls back to properties mavenCentralUsername and mavenCentralPassword respectively
     */
    /*
     * This publication can be sent to other destinations, e.g. GitHub
     * The task name would be 'publishAllPublicationsToGitHubRepository'
     */
    repository("https://maven.pkg.github.com/OWNER/REPOSITORY", "GitHub") {
        user = System.getenv("GITHUB_USERNAME")
        password = System.getenv("GITHUB_TOKEN")
    }
    /*
     * A simplified handler is available for publishing on the Snapshots repository of Maven Central
     */
    if (project.version.endsWith("-SNAPSHOT")) { // Avoid stable versions being pushed there...
        mavenCentralSnapshotRepository() // Imports user and password from the configuration for Maven Central
        // mavenCentralSnapshotRepository() {
        //     ...but they can be customized as per any other repository
        // }
    }
    /*
     * You may also want to configure publications created by other plugins
     * like the one that goes on Central. Typically, for instance, for publishing
     * Gradle plugins to Maven Central.
     * It can be done as follows.
     */
    publishing {
        publications {
            withType<MavenPublication> {
                configurePomForMavenCentral()
            }
        }
    }
}
/*
 * Developers and contributors must be added manually
 */
publishing {
    publications {
        withType<MavenPublication> {
            pom {
                developers {
                    developer {
                        name.set("Danilo Pianini")
                        email.set("danilo.pianini@gmail.com")
                        url.set("http://www.danilopianini.org/")
                    }
                }
            }
        }
    }
}
/*
 * The plugin automatically adds every publication to the list of objects to sign
 * The configuration of the signing process is left to the user, though,
 * as in a normal Gradle build.
 * In the following example, in-memory signing is configured.
 * For further options, please refer to: https://docs.gradle.org/current/userguide/signing_plugin.html
 */
signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}
```

### OSSRH Timeouts

The Maven Central infrastructure seems to be not always... responsive.
I often got build failures due to timeouts.
```
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':publishAllPublicationsToMavenCentralRepository'.
> Failed to publish publication 'mavenCentral' to repository 'maven'
   > Could not write to resource 'https://oss.sonatype.org/service/local/staging/deploy/maven2/my/group/my-artifact/0.1.0/my-artifact-0.1.0-.jar'.
      > Read timed out
```
My current workaround is to add the following in my `gradle.properties` file:
```
systemProp.org.gradle.internal.http.connectionTimeout=500000
systemProp.org.gradle.internal.http.socketTimeout=500000
```
which seem to instruct Gradle to be more tolerant with network delays.

## Contributing to the project

I gladly review pull requests and I'm happy to improve the work.
If the software was useful to you, please consider supporting my development activity
[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=5P4DSZE5DV4H2&currency_code=EUR)


