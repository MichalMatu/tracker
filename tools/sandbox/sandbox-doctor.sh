#!/usr/bin/env bash
set -u

printf '=== CPU ===\n'
printf 'workers='; nproc || true
printf '\n=== RAM ===\n'
free -h || true
printf '\n=== DISK ===\n'
df -h . || true
printf '\n=== JAVA ===\n'
printf 'JAVA_HOME=%s\n' "${JAVA_HOME:-}"
java -version 2>&1 || true
printf '\n=== KOTLIN ===\n'
command -v kotlinc || true
kotlinc -version 2>&1 || true
printf '\n=== BUILD ENV ===\n'
printf 'GRADLE_USER_HOME=%s\n' "${GRADLE_USER_HOME:-}"
printf 'ANDROID_HOME=%s\n' "${ANDROID_HOME:-}"
printf 'ANDROID_SDK_ROOT=%s\n' "${ANDROID_SDK_ROOT:-}"
printf '\n=== GIT ===\n'
git --version || true
git status --short --branch 2>/dev/null || true
printf '\n=== GRADLE ===\n'
if [[ -x ./gradlew ]]; then
  ./gradlew --version || true
else
  echo 'gradlew not found in current directory'
fi
