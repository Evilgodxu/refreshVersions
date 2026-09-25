pluginManagement {
    repositories {
        // 本地维护分支：统一走腾讯云镜像
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/") }
    }
}
