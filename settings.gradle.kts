import de.fayard.dependencies.bootstrapRefreshVersionsAndDependencies
import org.danilopianini.VersionAliases.justAdditionalAliases
buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        classpath("de.fayard:dependencies:0.+")
        classpath("org.danilopianini:refreshversions-aliases:0.+")
    }
}
bootstrapRefreshVersionsAndDependencies(justAdditionalAliases)
rootProject.name = "publish-on-central"

