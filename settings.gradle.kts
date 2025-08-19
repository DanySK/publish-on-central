plugins {
    id("com.gradle.develocity") version "4.1.1"
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.0.30"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        val inCI = System.getenv("CI") == true.toString()
        uploadInBackground = !inCI
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
