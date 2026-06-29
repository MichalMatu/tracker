pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tracker_android"
include(":app")
include(":core:model")
include(":core:decoders")
include(":core:domain")
include(":core:data")
include(":feature:radar")
include(":feature:details")
include(":feature:settings")
include(":feature:watchlist")

include(":core:ui")
