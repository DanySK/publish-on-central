# 0.6.1
* The `assemble` task (if existing) now depends on `sourcesJar` and `javadocJar`
* Removed references to `maven-central-gradle-plugin` in favor of `publish-on-central`

# 0.6.0

* Adds better support for the Snapshot repository of Central.
* Repository final configuration is now delayed and performed `afterEvaluate`.
* Improved internal structure
* Enabled a better quality assurance

# 0.5.0

* The default repository URL for Maven Central switches to `https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/`
  (see: [https://central.sonatype.org/publish/publish-gradle/](https://central.sonatype.org/publish/publish-gradle/))
