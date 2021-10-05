plugins {
    id("de.fayard.refreshVersions") version "0.10.1"
}
refreshVersions {
    featureFlags {
        enable(de.fayard.refreshVersions.core.FeatureFlag.LIBS)
    }
}
rootProject.name = "publish-on-central"
