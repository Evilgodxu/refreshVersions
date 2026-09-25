# refreshVersions 本地维护分支

Gradle 依赖版本巡检插件。上游已长期停更，本目录是自行维护的副本。

- 上游来源：`Splitties/refreshVersions` v0.60.6（MIT）
- 本地路径：`C:\Android\refreshVersions`
- 对外插件 ID：`de.fayard.refreshVersions`

## 为什么自行维护

上游最后版本 0.60.6 发布于 2025-08，此后长期停更。直接使用远程分发有两个问题：

1. **源码无法用新版 Gradle 编译。** 上游用 Gradle 8.14.3 打包，源码直接引用 Gradle 内部 API。本机项目跑 Gradle 9.8，`includeBuild` 会用 9.8 现场编译源码，因此必须改。
2. **出问题无法自助修复。** 与 Gradle 新版本的兼容性只能等上游，而上游已无人维护。

实测印证：Gradle 的 `Dependency` 接口在 9.x 中移除了 `contentEquals`，上游源码因此编译失败——这正是必须 fork 的直接原因。

## 接入方式

项目 `settings.gradle.kts`：

```kotlin
pluginManagement {
    includeBuild("../refreshVersions/plugins")
}

plugins {
    id("de.fayard.refreshVersions")   // 不带版本号，由本地源码提供
}
```

构建时由项目侧的 Gradle 编译本目录源码，插件不再从 Maven 仓库下载。

## 任务一览

| 任务 | 作用 |
|---|---|
| `refreshVersions` | 巡检所有依赖，把可用更新以注释写入 `gradle/libs.versions.toml` |
| `refreshVersionsCleanup` | 清除上述可用更新注释 |
| `refreshVersionsMigrate` | 迁移项目到版本占位符或版本目录，需配合 `--mode` 参数 |

上游官网文档：<https://splitties.github.io/refreshVersions/>（上游停更，内容可能过期）

## 与上游的差异

### 编译适配

| 文件 | 改动 |
|---|---|
| `plugins/core/build.gradle.kts` | `jvmToolchain(8)` → `17` |
| `plugins/dependencies/build.gradle.kts` | 同上 |
| `plugins/core/.../ConfigurationLessDependency.kt` | 移除 `contentEquals` override，Gradle 9 的 `Dependency` 接口已无此方法 |
| `plugins/core/.../PluginVersion.kt` | 新增。取代 `version-sync` 插件在构建期生成的同名常量 |

### 解除对上游发布物的依赖

| 文件 | 改动 |
|---|---|
| `plugins/settings.gradle.kts` | 移除 build scan、自举 `refreshVersions`、`version-sync` 三个插件声明；不再 include `buildSrcLibs` |
| `plugins/convention-plugins/settings.gradle.kts` | 移除自举插件与 `refreshVersions {}` 配置块 |
| `plugins/convention-plugins/build.gradle.kts` | 移除 `plugin-publish` 依赖与 `_` 版本占位符 |
| `plugins/convention-plugins/.../gradle-plugin.gradle.kts` | 发布与签名配置 → 只保留 `java-gradle-plugin` |
| `plugins/build.gradle.kts` | 移除 `plugin-publish` 声明 |
| `plugins/core/build.gradle.kts` | 依赖记法（`KotlinX.*` 等）改为显式坐标；移除 `putVersionInCode` 与 `build/gen` 源目录 |
| `plugins/dependencies/build.gradle.kts` | 依赖记法改为显式坐标；移除 `sourcesJar` 与发布钩子 |

### 已删除的内容

- `plugins/buildSrcLibs/`：为下游项目生成 buildSrc 常量的模块，此处用不到
- 发布逻辑：`convention-plugins/src/main/kotlin/publishing/`、`PropertyOrEnv.kt`
- 上游示例与站点：`sample-*`、`docs/`、`dummy-library-for-testing/`
- 上游协作与 CI 元数据：`.github/`、`.fleet/`、`CHANGELOG.md`、`CODEOWNERS`、`SPONSORS.md`、`jitpack.yml`、`justfile`、`mkdocs.yml`、`checkPlugins.sh`、`fix-the-ide.sh`
- `plugins/versions.properties`：自举插件移除后已无读取方
- `core/.../removals_replacement/` 内的两份上游设计文档：`Design doc.md`、`README.md`

## 环境要求

- **Gradle 9.8.0**：wrapper 已对齐到该版本（`distributionUrl`、`distributionSha256Sum`、`gradle-wrapper.jar`、`gradlew` 全部同步更新），主项目亦为 9.8.0。
- **JDK 17**：编译基线。Gradle 9 要求 Java 17+，用 17 编译可保证插件在任何 Gradle 9 环境加载；本机另有 JDK 25，未采用。

## 技术栈版本

上游依赖停留在 2020~2023 年，本地维护分支已对齐到当前最新稳定版（2026-09 核实）：

| 依赖 | 上游 | 本地维护 | 跨度 |
|---|---|---|---|
| Gradle | 8.14.3 | **9.8.0** | |
| kotlinx-coroutines-core | 1.7.3 | **1.11.0** | |
| okhttp / logging-interceptor | 4.12.0 | **5.5.0** | 主版本 |
| retrofit | 2.9.0 | **3.0.0** | 主版本 |
| moshi-kotlin | 1.11.0 | **1.15.2** | |
| junit-bom / junit-jupiter | 5.8.1 | **6.1.3** | 主版本 |
| kotest | 4.6.3 | **6.2.5** | 跨两个主版本 |
| kotlin-test | 1.8.10 | **2.4.20** | 主版本 |
| Kotlin `apiVersion` | 1.8 | **2.0** | |

升级后 core 135 个、dependencies 24 个测试全部通过。

**有意未升级的一项**：`jvmToolchain` 保持 17。它是编译目标而非依赖版本——17 是 Gradle 9 的最低要求，用它编译的字节码能在任意 Gradle 9 环境加载；换成 25 会让插件只能在 JDK 25 上运行。

## 构建与验证

```bash
# 编译插件
./gradlew -p C:/Android/refreshVersions/plugins :refreshVersions:jar :refreshVersions-core:jar

# 运行上游测试
./gradlew -p C:/Android/refreshVersions/plugins :refreshVersions-core:test

# 接入后，在主项目中巡检依赖版本
cd C:/Android/YiChaoMusic && ./gradlew refreshVersions
```

巡检结果以 `## ⬆ = "x.y.z"` 注释写入主项目的 `gradle/libs.versions.toml`，是否升级由人工决定。

## 已知限制

1. **构建链不碰远程，但不等于完全离线。** 编译 `core` 仍需从仓库拉取 coroutines / okhttp / retrofit2 / moshi；执行巡检任务时也需联网查询版本元数据——那是插件功能本身。
2. **Windows 长路径。** 测试资源中存在长度超过 225 字符的路径，解压与打包需加 `\\?\` 扩展前缀，否则触发 `MAX_PATH`。
3. **同步上游只能靠源码包。** 本机 `git` 无法访问 github.com（证书吊销检查失败），但 `codeload.github.com` 可用。

## 恢复上游原始版本

`C:\Android\refreshVersions-upstream-0.60.6.zip`（837 个文件）是未改动的上游源码快照，可用于找回被删内容或对比差异。

## 上游来源与许可

本项目是 [Splitties/refreshVersions](https://github.com/Splitties/refreshVersions) 的**非官方本地维护分支**，基于上游 **v0.60.6**（2025-08-15 发布）的源码修改，不包含上游的任何发布产物。

- 上游仓库：<https://github.com/Splitties/refreshVersions>
- 基线版本：`v0.60.6`（git tag）
- 未改动的上游源码快照：`C:\Android\refreshVersions-upstream-0.60.6.zip`（837 个文件，不随本仓库分发）
- 许可：MIT，见 `LICENSE.txt`；版权归原作者 Jean-Michel Fayard、Louis CAD 及其贡献者所有
- 本分支与上游的差异逐项列于上文「与上游的差异」章节

本分支的修改同样以 MIT 许可发布。
