#!/usr/bin/env bash
# ============================================================
# 预置 root build plugins 块所需插件构件到 mavenLocal (~/.m2)。
#
# 背景：GitHub runner 上 Gradle 的插件 marker 解析对所有远程仓库均报
# "could not resolve plugin artifact"（同机 curl 直取同一 URL 却返回
# 200，具体原因未明；普通依赖解析正常，仅插件 marker 通道异常）。
# mavenLocal 不经网络，确定性可用，作为插件解析的兜底前置源。
#
# 构件来源：Maven Central（repo.maven.apache.org）与
# Gradle Plugin Portal（plugins.gradle.org/m2，spotless 仅发布于此）。
# 版本须与 gradle/libs.versions.toml 保持同步。
# ============================================================
set -euo pipefail

CENTRAL="https://repo.maven.apache.org/maven2"
PORTAL="https://plugins.gradle.org/m2"
M2="$HOME/.m2/repository"

# seed <base_url> <group> <artifact> <version> <ext...>
seed() {
  local base="$1" g="$2" a="$3" v="$4"
  shift 4
  local gp="${g//./\/}"
  local dir="$M2/$gp/$a/$v"
  mkdir -p "$dir"
  local ext
  for ext in "$@"; do
    if [ ! -s "$dir/$a-$v.$ext" ]; then
      curl -sfL --retry 3 -m 180 -o "$dir/$a-$v.$ext" "$base/$gp/$a/$v/$a-$v.$ext"
      echo "seeded $g:$a:$v.$ext"
    fi
  done
}

# ── 插件 marker（仅 pom）─────────────────────────────────────
seed "$CENTRAL" com.google.devtools.ksp          com.google.devtools.ksp.gradle.plugin          2.3.11  pom
seed "$CENTRAL" org.jetbrains.kotlin.plugin.serialization org.jetbrains.kotlin.plugin.serialization.gradle.plugin 2.3.21 pom
seed "$CENTRAL" com.google.dagger.hilt.android   com.google.dagger.hilt.android.gradle.plugin   2.57.2  pom
seed "$CENTRAL" com.apollographql.apollo         com.apollographql.apollo.gradle.plugin         5.0.1   pom
seed "$PORTAL"  com.diffplug.spotless            com.diffplug.spotless.gradle.plugin            7.2.1   pom
seed "$CENTRAL" io.gitlab.arturbosch.detekt      io.gitlab.arturbosch.detekt.gradle.plugin      1.23.8  pom

# ── marker 指向的真实插件构件（pom + jar）────────────────────
seed "$CENTRAL" com.google.devtools.ksp          symbol-processing-gradle-plugin  2.3.11  pom jar
seed "$CENTRAL" org.jetbrains.kotlin             kotlin-serialization             2.3.21  pom jar
seed "$CENTRAL" com.google.dagger                hilt-android-gradle-plugin       2.57.2  pom jar
seed "$CENTRAL" com.apollographql.apollo         apollo-gradle-plugin             5.0.1   pom jar
seed "$CENTRAL" com.diffplug.spotless            spotless-plugin-gradle           7.2.1   pom jar
seed "$CENTRAL" io.gitlab.arturbosch.detekt      detekt-gradle-plugin             1.23.8  pom jar

echo "plugin seeding done"
