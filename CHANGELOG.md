# [0.7.0](https://github.com/DanySK/publish-on-central/compare/0.6.1...0.7.0) (2021-11-26)


### Bug Fixes

* add Nexus tasks for each publication ([9ad0d5b](https://github.com/DanySK/publish-on-central/commit/9ad0d5bd99b7b3570d0009790843b74e98ba19b2))
* adjust dependencies among tasks ([ae0f6ef](https://github.com/DanySK/publish-on-central/commit/ae0f6ef4eafb71d9ebaeb9602f7c57bf3173f02b))
* describe the release Nexus task ([a520c94](https://github.com/DanySK/publish-on-central/commit/a520c94e771bbd5424f887a2c4877fa1e3faac12))
* filter repository targets before binding Nexus ([29491a9](https://github.com/DanySK/publish-on-central/commit/29491a9bfd4f531e6a35c107cb9902f800314cd1))
* pass the credentials to Nexus ([ed9cd3e](https://github.com/DanySK/publish-on-central/commit/ed9cd3ec4a85835aec7421d44e0eff0f7fc6fcc1))
* provide a snapshot repository ([9cfed80](https://github.com/DanySK/publish-on-central/commit/9cfed80ab35283132459cbcf32920dcd51ce0155))
* remove printlns ([fd696a0](https://github.com/DanySK/publish-on-central/commit/fd696a0dd67ed39bf43e0226b7a962ffbb3cd034))
* require snapshot repositories for Nexus staging ([9c2182f](https://github.com/DanySK/publish-on-central/commit/9c2182f8324be0c896927dbb5f5b7096679c5e99))
* use the logger for informing on lifecycle progress ([3a00662](https://github.com/DanySK/publish-on-central/commit/3a00662da2f292b2a330d706889adc99d2b1f4ac))
* use transitioners ([f659e7b](https://github.com/DanySK/publish-on-central/commit/f659e7bda0e50a48b6c04ac3f19b36289c87b5ce))


### Features

* experimental support for Nexus ([6bba219](https://github.com/DanySK/publish-on-central/commit/6bba2192d919484f01fa04453fe09b113c8132f7))

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
