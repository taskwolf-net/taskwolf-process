#!/usr/bin/env bash
# Builds the Taskwolf modules this repository depends on from source and installs them into the
# local Maven repository, so CI needs neither a package registry nor credentials.
# access and device depend on each other, which rules out building the modules one after another.
# Instead all required modules are compiled together into one jar that is installed under every
# module's coordinates. The modules used to be published as fat jars, so each pom carries that
# module's implementation dependencies.
set -euo pipefail

base=${1:?usage: build-dependencies.sh <git base url, e.g. https://github.com/owner>}
work=build/taskwolf-dependencies
self=$(sed -n 's/^rootProject.name = "\(.*\)"/\1/p' settings.gradle.kts)
release=$(grep -oE 'VERSION_[0-9]+' build.gradle.kts | head -1 || true)
modules() { grep -oE '"net\.taskwolf:[a-z-]+' "$1" | cut -d: -f2 || true; }

queue=($(modules build.gradle.kts))
((${#queue[@]})) || exit 0
rm -rf "$work" && mkdir -p "$work/bundle"
seen=" "
while ((${#queue[@]})); do
  module=${queue[0]}
  queue=("${queue[@]:1}")
  [[ $seen == *" $module "* ]] && continue
  seen+="$module "
  dir=.
  if [[ $module != "$self" ]]; then
    dir=$work/$module
    git clone -q --depth 1 "$base/taskwolf-$module.git" "$dir"
  fi
  queue+=($(modules "$dir/build.gradle.kts"))
  echo "  java.srcDir(\"$(realpath "$dir")/src/main/java\")" >> "$work/sources"
  grep -hoE '^\s*(implementation|compileOnly|api)\("[^"]+:[^"]+:[^"]+"\)' "$dir/build.gradle.kts" \
    | grep -v 'net\.taskwolf' | sed -E 's/^\s*[a-zA-Z]+/  compileOnly/' >> "$work/dependencies" || true
  grep -hoE '^\s*(implementation|api)\("[^"]+:[^"]+:[^"]+"\)' "$dir/build.gradle.kts" \
    | grep -v 'net\.taskwolf' | cut -d'"' -f2 > "$work/$module.runtime" || true
done

echo 'rootProject.name = "bundle"' > "$work/bundle/settings.gradle.kts"
cat > "$work/bundle/build.gradle.kts" <<GRADLE
plugins {
  id("java")
  id("io.freefair.lombok") version "8.13"
}

java.sourceCompatibility = JavaVersion.${release:-VERSION_21}

repositories {
  mavenCentral()
}

dependencies {
$(sort -u "$work/dependencies")
}

sourceSets.main {
$(cat "$work/sources")
}
GRADLE
./gradlew -q -p "$work/bundle" jar

for module in $seen; do
  dir=$HOME/.m2/repository/net/taskwolf/$module/1.0.0-SNAPSHOT
  mkdir -p "$dir"
  cp "$work/bundle/build/libs/bundle.jar" "$dir/$module-1.0.0-SNAPSHOT.jar"
  cat > "$dir/$module-1.0.0-SNAPSHOT.pom" <<POM
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>net.taskwolf</groupId>
  <artifactId>$module</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <dependencies>
$(while IFS=: read -r group artifact version; do
  echo "    <dependency><groupId>$group</groupId><artifactId>$artifact</artifactId><version>$version</version></dependency>"
done < "$work/$module.runtime")
  </dependencies>
</project>
POM
done
