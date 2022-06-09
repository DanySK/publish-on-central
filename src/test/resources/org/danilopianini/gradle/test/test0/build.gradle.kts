plugins {
    `java-library`
    `java-gradle-plugin`
    id("org.danilopianini.publish-on-central")
}
group = "io.github.danysk"
publishOnCentral {
    projectDescription.set("test")
    repository("https://maven.pkg.github.com/OWNER/REPOSITORY") {
        name = "github"
        user.set("test")
        password.set("pwd")
    }
}
