pluginManagement {
    includeBuild("convention-plugins")
    repositories {
        // 本地维护分支：统一走腾讯云镜像，避免依赖 Plugin Portal 与 Central 的直连
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
    }
}

gradle.rootProject {
    loadLocalProperties()
}

gradle.beforeProject {
    group = "de.fayard.refreshVersions"
}

// 本地维护分支：不再应用 build scan、自举 refreshVersions 与 version-sync 插件，
// 构建脚本改用显式坐标，避免构建自身依赖插件发布物
include("core", "dependencies")
project(":core").name = "refreshVersions-core"
project(":dependencies").name = "refreshVersions"

fun Project.loadLocalProperties() {
    val localPropertiesFile = rootDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        val localProperties = java.util.Properties()
        localProperties.load(localPropertiesFile.inputStream())
        localProperties.forEach { (k, v) -> if (k is String) project.extra.set(k, v) }
    }
}
