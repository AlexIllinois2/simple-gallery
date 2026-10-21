# 项目长期记忆 — Simple-Gallery（修改版）

## 工具链（2026-10 起）
- AGP **8.1.2** / Kotlin **1.9.10** / KSP **1.9.10-1.0.13** / Gradle 8.2.1 / JDK 17 / compileSdk 34。
- 升级原因：内联的 `commons` 模块用 Compose，需 Kotlin 1.9 + Compose Compiler 1.5.3；AGP 7.4 的 D8 无法处理 Kotlin 1.9 metadata（报 `ERROR:D8: com.android.tools.r8.kotlin.H`）。
- 版本统一在 `gradle/libs.versions.toml`；新增第三方依赖请走 version catalog。

## 模块结构
- `:app` 主应用（flavors: foss / proprietary / prepaid）。
- `:commons` Simple-Commons 源码内联（上游 `ea86877113`，命名空间 `com.simplemobiletools.commons`），不再使用 JitPack AAR；`app` 用 `implementation(project(":commons"))`。
- 插件别名必须在根 `build.gradle.kts` 以 `.apply(false)` 声明，子模块再 `alias(...)`，否则 “already on the classpath with an unknown version”。

## 构建
- bash 下无 `java`，构建需 `JAVA_HOME="/c/Users/45574/.workbuddy/binaries/jdk/jdk-17.0.20.1+1" ./gradlew ...`。
- 默认 release：`./gradlew assembleFossRelease`（签名读 `keystore.properties`）。
- 全量首次构建约 7-9 分钟（Compose + R8）。
