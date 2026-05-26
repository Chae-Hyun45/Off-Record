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

        // 🌐 MPAndroidChart 라이브러리를 찾기 위한 JitPack 저장소 추가
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Off-Record"
include(":app")
 