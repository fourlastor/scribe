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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()

        // sherpa-onnx publishes its Android AAR only as a GitHub release asset.
        exclusiveContent {
            forRepository {
                ivy {
                    name = "sherpaOnnxReleases"
                    url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
                    patternLayout { artifact("v[revision]/[artifact]-[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.k2fsa", "sherpa-onnx") }
        }

        // Speech models are resolved like any other dependency, so Gradle downloads them once and
        // keeps them in its dependency cache. See the model catalogue in app/build.gradle.kts.
        exclusiveContent {
            forRepository {
                ivy {
                    name = "huggingFace"
                    url = uri("https://huggingface.co")
                    patternLayout { artifact("[organisation]/[module]/resolve/[revision]/[artifact].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter {
                includeGroup("csukuangfj")
                includeGroup("csukuangfj2")
            }
        }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "sherpaOnnxModels"
                    url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
                    patternLayout { artifact("[revision]/[artifact].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("com.k2fsa.models") }
        }
    }
}

rootProject.name = "scribe"
include(":app")
