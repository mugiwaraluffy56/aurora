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

rootProject.name = "Aurora"
include(":app")
include(":core")
include(":domain")
include(":data")
include(":engine")
include(":feature")
include(":design-system")
include(":testing")
