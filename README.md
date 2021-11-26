# publish-on-central
A Gradle plugin for streamlined publishing on Maven Central
(and other Maven / Nexus repositories).
This plugin is meant to provide an even easier configuration than
[`io.github.gradle-nexus:publish-plugin`](https://github.com/gradle-nexus/publish-plugin)
(from which this plugin depends),
with the goal of supporting highly automated workflows with minimal configuration.

## Rationale
Publishing on Maven Central requires too much configuration?
Well, I agree.
This plugin is here to simplify your life by automatically
scanning all the software components produced by any plugin,
configuring a corresponding publication,
filling all the information required by OSSRH,
configuring tasks for generating javadoc and source jar files,
activating the signing plugin,
and preparing tasks to upload, close, and release the artifact.

This plugin supports both targets that use Sonatype Nexus (such as Maven Central)
and targets that do not, such as GitHub Packages.

### Provided tasks

* `sourcesJar`: a `Jar` task preconfigured to collect and pack `allSource` from the `main` source set
* `javadocJar`: a `Jar` task preconfigured to
    1. Detect if a javadoc tasks exists, and in case depend on it, and pack its output folder
    2. Detect if a dokkaJavadoc tasks exists, and to the same as above
* One task for each combination of `SoftwareComponent` and repository,
  unless manually deactivated, a `MavenCentral` repository is created by default.
* One task for publishing `All` software components to any target repository
* For every repository with an associated Sonatype Nexus instance, additional tasks are generated to control the
  upload into a new staging repository, its closure, and its release.

In short, if you have (for instance) a mixed Java-Kotlin project,
you should find the following tasks:

* `closeJavaMavenOnMavenCentralNexus`
* `closeKotlinMavenOnMavenCentralNexus`
* `publishJavaMavenPublicationToMavenCentralRepository`
* `publishKotlinMavenPublicationToMavenCentralRepository`
* `publishAllPublicationsToMavenCentralRepository`
* `publishJavaMavenPublicationToMavenLocalRepository`
* `publishKotlinMavenPublicationToMavenLocalRepository`
* `publishAllPublicationsToMavenLocalRepository`
* `releaseJavaMavenOnMavenCentralNexus`
* `releaseKotlinMavenOnMavenCentralNexus`
* `uploadJavaMavenToMavenCentralNexus`
* `uploadKotlinMavenToMavenCentralNexus`

If you add a custom repository, say `myRepo`, you would also find the following tasks:

* `publishJavaMavenPublicationToMyRepoRepository`
* `publishKotlinMavenPublicationToMyRepoRepository`
* `publishAllPublicationsToMyRepoRepository`

and if `myRepo` has configured an URL for an associated Nexus instance, the following ones:

* `publishJavaMavenPublicationToMyRepoRepository`
* `publishKotlinMavenPublicationToMyRepoRepository`
* `publishAllPublicationsToMyRepoRepository`


which is what needs to get called to have your artifacts uploaded on OSSRH Nexus instance.

### Importing the plugin

```kotlin
plugins {
    id ("org.danilopianini.publish-on-central") version "<pick the latest>")
}
```
The plugin is configured to react to the application of the `java` plugin, and to apply the `maven-publish` and `signing` plugin if they are not applied.

### Configuring the plugin

```kotlin
// The package name is equal to the project name
group = "your.group.id" // This must be configured for the generated pom.xml to work correctly
/*
 * The plugin comes with defaults that are useful to myself. You should configure it to behave as you please:
 */
publishOnCentral {
    // Set to false if you do not want the MavenCentral repository to be automatically configured
    configureMavenCentral.set(true)
    // The following values are the default, if they are ok with you, just omit them
    projectDescription.set("No description provided")
    projectLongName.set(project.name)
    licenseName.set("Apache License, Version 2.0")
    licenseUrl.set("http://www.apache.org/licenses/LICENSE-2.0")
    projectUrl.set("https://github.com/DanySK/${project.name}")
    scmConnection.set("git:git@github.com:DanySK/${project.name}")
    /*
     * The plugin is pre-configured to fetch credentials for Maven Central from the context in the following order:
     * 1. Environment variables MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD
     * 2. Project properties mavenCentralUsername and mavenCentralPassword
     * 3. Project properties sonatypeUsername and sonatypePassword
     * 4. Project properties ossrhUsername and ossrhPassword
     * 
     * They can be further customized through values or providers:
     */
    mavenCentral.user.set("...")
    mavenCentral.password.set(provider { "..." })

    /*
     * The publications can be sent to other destinations, e.g. GitHub
     * The task name would be 'publishAllPublicationsToGitHubRepository'
     */
    repository("https://maven.pkg.github.com/OWNER/REPOSITORY", "GitHub") {
        user.set(System.getenv("GITHUB_USERNAME"))
        password.set(System.getenv("GITHUB_TOKEN"))
    }
  
    /*
     * Here is an example of a repository with a custom Nexus instance
     */
    repository("https://some/valid/repo/with/nexus", "MyNexus") {
        user.set(mavenCentral.user) // mavenCentral is accessible for 
        password.set(System.getenv("GITHUB_TOKEN"))
        nexusUrl = "https://some/valid/nexus/instance"
        // nexusTimeOut and nexusConnectionTimeOut can be configured, too.
    }
    /*
     * A simplified handler is available for publishing on the Snapshots repository of Maven Central
     */
    if (project.version.endsWith("-SNAPSHOT")) { // Avoid stable versions being pushed there...
      mavenCentralSnapshotsRepository() // Imports user and password from the configuration for Maven Central
        // mavenCentralSnapshotsRepository() {
        //     ...but they can be customized as per any other repository
        // }
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


