plugins {
    id("org.danilopianini.publish-on-central")
    id("org.danilopianini.multi-jvm-test-plugin") version "0.5.6"
}
group = "io.github.danysk"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

/*
subprojects {
    apply(plugin = "org.danilopianini.publish-on-central")
}
 */
