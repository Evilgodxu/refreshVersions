# refreshVersions — Local Maintenance Branch

[中文](README.md) | **English**

A Gradle plugin for inspecting dependency versions. Upstream has been unmaintained for a long time, so this repository is a self-maintained copy.

- Upstream: `Splitties/refreshVersions` v0.60.6 (MIT)
- Plugin ID: `de.fayard.refreshVersions`
- Current version: `v1.0.0`

## Why this fork exists

Upstream's last release, 0.60.6, dates from 2025-08 and nothing has shipped since. Relying on the published artifact has two problems:

1. **The source cannot be compiled with a recent Gradle.** Upstream was built with Gradle 8.14.3 and its source references Gradle internal APIs directly. The host project runs Gradle 9.8, and `includeBuild` compiles the source on the fly with 9.8 — so it has to be fixed.
2. **Problems cannot be fixed independently.** Compatibility with new Gradle releases can only come from upstream, and upstream is no longer maintained.

This was confirmed in practice: Gradle's `Dependency` interface dropped `contentEquals` in 9.x, which breaks compilation of the upstream source. That is the direct reason this fork exists.

## How to consume it

In the host project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("../refreshVersions/plugins")   // points at this repo's plugins directory
}

plugins {
    id("de.fayard.refreshVersions")   // no version — supplied by local source
}
```

The plugin is compiled from this repository's source by the host project's own Gradle. It is never downloaded from a Maven repository.

## Tasks

| Task | Purpose |
|---|---|
| `refreshVersions` | Scans all dependencies and writes available updates as comments into `gradle/libs.versions.toml` |
| `refreshVersionsCleanup` | Removes those availability comments |
| `refreshVersionsMigrate` | Migrates a project to version placeholders or a version catalog; requires `--mode` |

Upstream documentation: <https://splitties.github.io/refreshVersions/> (unmaintained, may be outdated)

## Differences from upstream

### Build adaptation

| File | Change |
|---|---|
| `plugins/core/build.gradle.kts` | `jvmToolchain(8)` → `17` |
| `plugins/dependencies/build.gradle.kts` | Same as above |
| `plugins/core/.../ConfigurationLessDependency.kt` | Removed the `contentEquals` override; the method no longer exists on Gradle 9's `Dependency` interface |
| `plugins/core/.../PluginVersion.kt` | New file. Replaces the constant previously generated at build time by the `version-sync` plugin |

### Removing the dependency on upstream's published artifacts

| File | Change |
|---|---|
| `plugins/settings.gradle.kts` | Removed the build scan, self-bootstrapping `refreshVersions`, and `version-sync` plugin declarations; `buildSrcLibs` is no longer included |
| `plugins/convention-plugins/settings.gradle.kts` | Removed the bootstrapping plugin and the `refreshVersions {}` configuration block |
| `plugins/convention-plugins/build.gradle.kts` | Removed the `plugin-publish` dependency and the `_` version placeholder |
| `plugins/convention-plugins/.../gradle-plugin.gradle.kts` | Publishing and signing configuration → only `java-gradle-plugin` remains |
| `plugins/build.gradle.kts` | Removed the `plugin-publish` declaration |
| `plugins/core/build.gradle.kts` | Dependency notations (`KotlinX.*` etc.) replaced with explicit coordinates; removed `putVersionInCode` and the `build/gen` source directory |
| `plugins/dependencies/build.gradle.kts` | Dependency notations replaced with explicit coordinates; removed the `sourcesJar` and publishing hooks |

### Removed content

- `plugins/buildSrcLibs/` — a module that generates buildSrc constants for downstream projects; unused here
- Publishing logic: `convention-plugins/src/main/kotlin/publishing/`, `PropertyOrEnv.kt`
- Upstream samples and site: `sample-*`, `docs/`, `dummy-library-for-testing/`
- Upstream collaboration and CI metadata: `.github/`, `.fleet/`, `CHANGELOG.md`, `CODEOWNERS`, `SPONSORS.md`, `jitpack.yml`, `justfile`, `mkdocs.yml`, `checkPlugins.sh`, `fix-the-ide.sh`
- `plugins/versions.properties` — nothing reads it once the bootstrapping plugin is gone
- Two upstream design documents under `core/.../removals_replacement/`: `Design doc.md`, `README.md`

## Requirements

- **Gradle 9.8.0** — the wrapper is aligned to this version (`distributionUrl`, `distributionSha256Sum`, `gradle-wrapper.jar` and `gradlew` were all updated together).
- **JDK 17** — the compilation baseline. Gradle 9 requires Java 17+, and building against 17 guarantees the plugin loads in any Gradle 9 environment.

## Toolchain versions

Upstream's dependencies date from 2020–2023. This branch is aligned with the current latest stable releases (verified 2026-09):

| Dependency | Upstream | This branch | Span |
|---|---|---|---|
| Gradle | 8.14.3 | **9.8.0** | |
| kotlinx-coroutines-core | 1.7.3 | **1.11.0** | |
| okhttp / logging-interceptor | 4.12.0 | **5.5.0** | major |
| retrofit | 2.9.0 | **3.0.0** | major |
| moshi-kotlin | 1.11.0 | **1.15.2** | |
| junit-bom / junit-jupiter | 5.8.1 | **6.1.3** | major |
| kotest | 4.6.3 | **6.2.5** | two majors |
| kotlin-test | 1.8.10 | **2.4.20** | major |
| Kotlin `apiVersion` | 1.8 | **2.0** | |

After the upgrade, all 135 tests in `core` and 24 in `dependencies` pass.

**One deliberate non-upgrade**: `jvmToolchain` stays at 17. It is a compilation target rather than a dependency version — 17 is Gradle 9's minimum, so bytecode built with it loads in any Gradle 9 environment; raising it would restrict the plugin to that specific JDK.

## Versioning

This branch is numbered independently, starting at **1.0.0** and incrementing each digit with carry (after `1.0.9` comes `1.1.0`). The upstream `0.60.x` sequence is not continued. The upstream baseline is `v0.60.6`; this branch's first release is `v1.0.0`.

The version is maintained in two places, which must be updated together:

| Location | Role |
|---|---|
| `plugins/version.txt` | Read at build time; also drives the removals-revision mapping |
| `plugins/core/src/main/kotlin/de/fayard/refreshVersions/PluginVersion.kt` | Used at runtime via `thisProjectVersion` |

After changing the version, run a build once and `plugins/dependencies/src/main/resources/version-to-removals-revision-mapping.txt` gains a `<version>-><revision>` line automatically.

## Build and verify

Run these from the repository root:

```bash
# Compile the plugins
./gradlew -p plugins :refreshVersions:jar :refreshVersions-core:jar

# Run the tests
./gradlew -p plugins :refreshVersions-core:test :refreshVersions:test

# Once wired up, scan for dependency updates from the host project
cd <host-project> && ./gradlew refreshVersions
```

Results are written as `## ⬆ = "x.y.z"` comments into the host project's `gradle/libs.versions.toml`; whether to upgrade remains a human decision.

## Known limitations

1. **The build chain avoids the network, but is not fully offline.** Compiling `core` still pulls coroutines / okhttp / retrofit2 / moshi from a repository, and the scan task queries version metadata over the network — that is the plugin's function itself.
2. **Windows long paths.** Test resources contain paths longer than 225 characters; extraction and archiving need the `\\?\` extended prefix, otherwise `MAX_PATH` is hit.
3. **Syncing upstream can only be done via source archives.** Where `git` cannot reach github.com directly (commonly a certificate revocation check failure), use `codeload.github.com` to download the source archive instead.

## Upstream and license

This project is an **unofficial local maintenance branch** of [Splitties/refreshVersions](https://github.com/Splitties/refreshVersions), derived from upstream **v0.60.6** (released 2025-08-15). It contains none of upstream's published artifacts.

- Upstream repository: <https://github.com/Splitties/refreshVersions>
- Baseline version: `v0.60.6` (git tag)
- Unmodified upstream source snapshot: kept locally, not distributed with this repository; re-obtainable from the upstream `v0.60.6` tag
- License: MIT, see `LICENSE.txt`; copyright belongs to Jean-Michel Fayard, Louis CAD and the upstream contributors
- Every difference from upstream is listed in the "Differences from upstream" section above

Changes made in this branch are released under the same MIT license.
