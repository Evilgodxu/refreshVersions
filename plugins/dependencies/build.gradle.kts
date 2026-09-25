import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("gradle-plugin")
    `kotlin-dsl`
    `jvm-test-suite`
    idea
}

gradlePlugin {
    plugins {
        create("refreshVersions") {
            id = "de.fayard.refreshVersions"
            displayName = "Typesafe Gradle Dependencies"
            description = "Common Gradle dependencies - See gradle refreshVersions"
            tags = listOf("dependencies", "versions", "buildSrc", "kotlin", "kotlin-dsl")
            implementationClass = "de.fayard.refreshVersions.RefreshVersionsPlugin"
        }
    }
}

dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:6.2.5")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher") {
        because("allows tests to run from IDEs that bundle older version of launcher")
    }

    testImplementation(testFixtures(project(":refreshVersions-core")))

    implementation(gradleKotlinDsl())
    api(project(":refreshVersions-core"))
    // 本地维护分支：依赖改用显式坐标，解除构建自身对 refreshVersions 插件发布物的依赖
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

val genResourcesDir = layout.buildDirectory.dir("generated/refreshVersions/resources")

sourceSets.main {
    resources.srcDir(genResourcesDir.get().asFile.path)
}

idea {
    module.generatedSourceDirs.add(genResourcesDir.get().asFile)
}

val copyDependencyNotationsRemovalsRevisionNumber by tasks.registering {
    val versionFile = rootProject.file("version.txt")
    val removalsRevisionHistoryFile = file("src/main/resources/removals-revisions-history.md")
    val snapshotDependencyNotationsRemovalsRevisionNumberFile = genResourcesDir.get().file("snapshot-dpdc-rm-rev.txt").asFile
    val versionToRemovalsMappingFile = file("src/main/resources/version-to-removals-revision-mapping.txt")


    inputs.files(versionFile, removalsRevisionHistoryFile)
    outputs.files(snapshotDependencyNotationsRemovalsRevisionNumberFile, versionToRemovalsMappingFile)

    doFirst {
        val version = versionFile.useLines { it.first() }
        val removalsRevision: Int? = removalsRevisionHistoryFile.useLines { lines ->
            lines.lastOrNull { it.startsWith("## ") }?.takeUnless { it.startsWith("## [WIP]") }
        }?.substringAfter("## Revision ")?.substringBefore(' ')?.toInt()
        if (version.endsWith("-SNAPSHOT")) {
            snapshotDependencyNotationsRemovalsRevisionNumberFile.let {
                when (removalsRevision) {
                    null -> it.delete()
                    else -> it.writeText(removalsRevision.toString())
                }
            }
        } else {
            snapshotDependencyNotationsRemovalsRevisionNumberFile.delete()
            val expectedPrefix = "$version->"
            val mappingLine = "$expectedPrefix$removalsRevision"
            val mappingFileContent = versionToRemovalsMappingFile.readText()
            val existingMapping = mappingFileContent.lineSequence().firstOrNull {
                it.startsWith(expectedPrefix)
            }
            if (existingMapping != null) {
                check(existingMapping == mappingLine)
            } else {
                check(mappingFileContent.endsWith('\n') || mappingFileContent.isEmpty())
                val isInCi = System.getenv("CI") == "true"
                check(isInCi.not()) {
                    "$versionToRemovalsMappingFile shall be updated before publishing."
                }
                versionToRemovalsMappingFile.appendText("$mappingLine\n")
            }
        }
    }
}

tasks.processResources.configure {
    dependsOn(copyDependencyNotationsRemovalsRevisionNumber)
}

@Suppress("UnstableApiUsage")
val prePublishTest = testing.suites.create<JvmTestSuite>("prePublishTest") {
    useJUnitJupiter()
    dependencies {
        implementation(project())
        implementation(testFixtures(project(":refreshVersions-core")))
        implementation("io.kotest:kotest-assertions-core:6.2.5")
    }
}

kotlin {
    target.compilations.let {
        it.getByName("prePublishTest").associateWith(it.getByName("main"))
    }
}

tasks.check {
    dependsOn(prePublishTest)
}

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
