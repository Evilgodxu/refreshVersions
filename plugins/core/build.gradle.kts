import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("gradle-plugin")
    `java-test-fixtures`
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("refreshVersions-core") {
            id = "de.fayard.refreshVersions-core"
            displayName = "./gradlew refreshVersions"
            description = "Painless dependencies management"
            tags = listOf("dependencies", "versions", "buildSrc", "kotlin", "kotlin-dsl")
            implementationClass = "de.fayard.refreshVersions.core.RefreshVersionsCorePlugin"
        }
    }
}

dependencies {
    compileOnly(gradleKotlinDsl())
    // 本地维护分支：依赖改用显式坐标，解除构建自身对 refreshVersions 插件发布物的依赖
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")!!.apply {
        because("It has ready to use HttpException class")
    }
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")

    testImplementation("com.squareup.okhttp3:logging-interceptor:5.5.0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("io.kotest:kotest-runner-junit5:6.2.5")
    testImplementation("org.jetbrains.kotlin:kotlin-test-annotations-common:2.4.20")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.4.20")

    testFixturesApi("com.squareup.okhttp3:okhttp:5.5.0")
    testFixturesApi("com.squareup.okhttp3:logging-interceptor:5.5.0")
    testFixturesApi("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testFixturesApi("org.jetbrains.kotlin:kotlin-test-annotations-common:2.4.20")
    testFixturesApi("org.jetbrains.kotlin:kotlin-test-junit5:2.4.20")
}

kotlin {
    target.compilations.let {
        it.getByName("testFixtures").associateWith(it.getByName("main"))
    }
}

(components["java"] as AdhocComponentWithVariants).let { javaComponent ->
    javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}

// 本地维护分支：版本常量改为源码内维护，不再由 version-sync 插件在构建期生成

kotlin {
    // 本地维护分支：编译基线由 JDK 8 提升至 17（本机无 JDK 8，且 Gradle 9 运行要求 17+）
    jvmToolchain(17)
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0 // Gradle 9 内嵌 Kotlin 2.x，据此设定下限
        freeCompilerArgs.add("-opt-in=de.fayard.refreshVersions.core.internal.InternalRefreshVersionsApi")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
