rootProject.name = "react-inference-rcp"

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}

// Builds against a local plugin checkout instead of the jitpack api when it sits next to this repository.
val localPlugin = file("../react-vector")
if (System.getenv("CI") == null && localPlugin.resolve("api").isDirectory) {
    includeBuild(localPlugin) {
        dependencySubstitution {
            substitute(module("com.github.react-minecraft.plugin:react-api")).using(project(":api"))
        }
    }
}
