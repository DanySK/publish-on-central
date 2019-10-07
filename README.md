# maven-central-gradle-plugin
A Gradle plugin for streamlined publishing on Maven Central

## Rationale
Publishing on Maven Central requires too much configuration?
Well, I agree.
This plugin is here to simplify your life by automatically configuring (when applied) the Java plugin and the Publish
Plugin to create source and javadoc jars, sign them for you and send them to OSSRH's Sonatype Nexus.

## Usage

### Importing the plugin

```kotlin
plugins {
    `java`
    `maven-publish`
    id ("org.danilopianini.publish-on-central") version "0.2.0"
}
```
These three plugins must be applied.
If you do not apply one of them, the plugin won't apply: it reacts to the application of both.

### Configuring the plugin

```kotlin
group = "your.group.id" // This must be configured for the generated pom.xml to work correctly
/*
 * The plugin comes with defaults that are useful to myself. You should configure it to behave as you please:
 */
publishOnCentral {
    projectDescription.set("description") // Defaults to "No description provided"
    projectLongName.set("full project name") // Defaults to the project name
    licenseName.set("your license") // Defaults to "Apache License, Version 2.0"
    licenseUrl.set("link to your license") // Defaults to http://www.apache.org/licenses/LICENSE-2.0
    projectUrl.set("website url") // Defaults to "https://github.com/DanySK/${project.name}"
    scmConnection.set("git:git@github.com:youruser/yourrepo") // Defaults to "git:git@github.com:DanySK/${project.name}"
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
    id ("org.danilopianini.publish-on-central") version "0.2.0"
}
```

### OSSRH credentials
Credentials for upload to Maven Central must be specified as gradle properties or environment properties, named respectively `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`.
You have fundamentally three choices:
1. set them up in a `gradle.properties` file in `GRADLE_HOME`;
2. pass the credentials with the command line;
3. write them in your project local `gradle.properties` (don't).
4. set them up in your environment (preferred for continuous integration)



### Adding developers

By default, the plugin does not add any developer or contributor to the generated pom file.
You can add your team as follows:

```kotlin
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
```

## Contributing to the project

I gladly review pull requests and I'm happy to improve the work.
If the software was useful to you, please consider supporting my development activity
[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=5P4DSZE5DV4H2&currency_code=EUR)


