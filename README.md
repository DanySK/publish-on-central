# maven-central-gradle-plugin
A Gradle plugin for streamlined publishing on Maven Central

## Rationale
Publishing on Maven Central requires too much configuration?
Well, I agree.
This plugin is here to simplify your life by automatically configuring (when applied) the Java plugin and the Publish Plugin to create source and javadoc jars, sign them for you and send them to OSSRH's Sonatype Nexus.

## Usage

### Importing the plugin

```kotlin
plugins {
    `java`
    `maven-publish`
    id ("org.danilopianini.publish-on-central") version "0.1.0"
}
```
These three plugins must be applied.
If you do not apply one of them, the plugin won't apply: it reacts to the application of both.

### Configuring the plugin

```kotlin
/*
 * The plugin comes with defaults that are useful to myself. You should configure it to behave as you please:
 */
publishOnCentral {
    projectDescription.set("description") // Defaults to "No description provided"
    projectLongName.set("full project name") // Defaults to the project name
    licenseName.set("your license") // Defaults to "Apache License, Version 2.0"
    licenseUrl.set("link to your license") // Defaults to http://www.apache.org/licenses/LICENSE-2.0
    projectUrl.set("website url") // Defaults to "git:git@github.com:DanySK/${project.name}"
    scmConnection.set("git:git@github.com:youruser/yourrepo") // Defaults to 
}
```

### Signing artifacts

You likely want your artifact to be signed.
To do so, you must have a correctly configured GPG key, and the signign plugin enabled:
```kotlin
plugins {
    `java`
    `maven-publish`
    `signing`
    id ("org.danilopianini.publish-on-central") version "0.1.0"
}
```
Now, the plugin won't sing automatically in order to prevent build breackages outside systems configured with the GPG keys.
Signing is enabled by adding in `gradle.properties`:
```gradle.properties
signArchivesIsEnabled = true
```
or by passing the property to the gradle executable with `-PsignArchivesIsEnabled=true`

### OSSRH credentials
Credentials for upload to Maven Central must be specified as gradle properties.
You have fundamentally three choices:
1. set them up in a `gradle.properties` file in `GRADLE_HOME`;
2. pass the credentials with the command line;
3. write them in your project local `gradle.properties` (don't).

You should still have a pleaceholder in your local `gradle.properties`:
```
signArchivesIsEnabled = false
ossrhUsername = YourUserName
ossrhPassword = SetThisOneElsewhere!
```

### Adding developers

By default, the plugin does not add any developer or contributor to the generated pom file.
You can add your team as follows:

```kotlin
publishing {
    publications {
        withType<MavenPublication>() {
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
```
