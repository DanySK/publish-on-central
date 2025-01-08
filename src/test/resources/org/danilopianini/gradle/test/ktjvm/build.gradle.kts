plugins {
    kotlin("jvm")
    id("org.danilopianini.publish-on-central")
}
group = "io.github.danysk"
version = "0.1.0"
publishOnCentral {
    repoOwner.set("test")
    projectDescription.set("test")
    repository("https://maven.pkg.github.com/OWNER/REPOSITORY") {
        name = "github"
        user.set("test")
        password.set("pwd")
    }
}
tasks.withType<Sign>().configureEach {
    enabled = false
}
