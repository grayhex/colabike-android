pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // The push SDK of RuStore (docs/adr/0017): its own repository, and nothing else is taken
        // from it, so another group can never be resolved from there.
        maven("https://nexus-external.rustore.ru/repository/maven-rustore-exposed/") {
            content { includeGroup("ru.rustore.sdk") }
        }
    }
}

rootProject.name = "colabike-android"

include(":app")

include(":core:model")

include(":core:network")

include(":core:auth")

include(":core:designsystem")
