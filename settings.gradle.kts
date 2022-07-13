plugins {
    id("com.gradle.enterprise") version "3.10.3"
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.0.15"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishOnFailure()
    }
}

gitHooks {
    commitMsg { conventionalCommits() }
    preCommit {
        tasks("ktlintCheck", "detekt")
    }
    createHooks(overwriteExisting = true)
}

rootProject.name = "publish-on-central"
enableFeaturePreview("VERSION_CATALOGS")
