plugins {
    java
    id("org.danilopianini.publish-on-central")
    id("org.danilopianini.multi-jvm-test-plugin") version "4.0.1"
}
group = "io.github.danysk"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
