pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        jcenter()
        mavenCentral()
        maven(url = "https://artifactory.img.ly/artifactory/imgly")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // local mirror with artifacts that are no longer resolvable from their original repos
        // (jcenter is gone, google VR sdk was removed from the google maven, jitpack builds fail)
        maven { url = uri("file:///C:/Users/45574/.gradle/local-m2") }
        google()
        jcenter()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven(url = "https://artifactory.img.ly/artifactory/imgly")
    }
}

rootProject.name = "Simple-Gallery"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(":app")

// TODO: This will be deprecated in future. Migrate to the newer `pluginManagement { includeBuild() }` mechanism instead of explicitly substituting dependency.
/*includeBuild("../Simple-Commons") {
    dependencySubstitution {
        substitute(module("com.github.SimpleMobileTools:Simple-Commons")).using(project(":commons"))
    }
}*/
