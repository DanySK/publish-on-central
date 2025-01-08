plugins {
    id("com.gradle.develocity") version "3.19"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        val inCI = System.getenv("CI") == true.toString()
        uploadInBackground = !inCI
    }
}
rootProject.name = "test-publish-on-central"
