const publishCmd = `
git tag -a -f \${nextRelease.version} \${nextRelease.version} -F CHANGELOG.md
git push --force origin \${nextRelease.version}
./gradlew publishPlugins -Pgradle.publish.key=$GRADLE_PUBLISH_KEY -Pgradle.publish.secret=$GRADLE_PUBLISH_SECRET || exit 1
./gradlew uploadKotlin uploadPluginMavenToMavenCentralNexus uploadPublishOnCentralPluginPluginMarkerMavenToMavenCentralNexus releaseStagingRepositoryOnMavenCentral --parallel || exit 2
./gradlew publishKotlinOSSRHPublicationToGithubRepository publishPluginMavenPublicationToGithubRepository || true
`
import config from 'semantic-release-preconfigured-conventional-commits' with { type: "json" };
config.plugins.push(
    ["@semantic-release/exec", {
        "publishCmd": publishCmd,
    }],
    "@semantic-release/github",
    "@semantic-release/git",
)
export default config
