#!/bin/sh
# Build an exact snapshot even after a partial commit in a dirty checkout.
set -eu
cd "$(git rev-parse --show-toplevel)"
commit=$(git rev-parse --verify HEAD)
revision=$(printf '%.12s' "$commit")
snapshot=$(mktemp -d)
pending=''
cleanup() {
    rm -rf -- "$snapshot"
    if [ -n "$pending" ]; then rm -f -- "$pending"; fi
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

git archive --format=tar --output="$snapshot/source.tar" "$commit" -- . ':(exclude)releases'
tar -xf "$snapshot/source.tar" -C "$snapshot"
if [ -f local.properties ]; then cp local.properties "$snapshot/"; fi

if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
    studio_jdk="${PROGRAMFILES:-C:/Program Files}/Android/Android Studio/jbr"
    if [ -x "$studio_jdk/bin/java.exe" ]; then export JAVA_HOME="$studio_jdk"; fi
fi

version=$(cd "$snapshot" && sh gradlew -q :app:printVersion)
# Gradle on Windows emits CRLF even when invoked from Git Bash.
version=$(printf '%s' "$version" | tr -d '\r')
destination="releases/phosphor-$version+g$revision-debug.apk"
if git ls-files --error-unmatch -- "$destination" >/dev/null 2>&1; then
    echo "Already published: $destination"
    exit 0
fi
(cd "$snapshot" && sh gradlew "-PphosphorDebugRevision=$revision" :app:assembleDebug)
mkdir -p releases
pending=$(mktemp releases/.debug-apk.XXXXXX)
cp "$snapshot/app/build/outputs/apk/debug/app-debug.apk" "$pending"
mv -f -- "$pending" "$destination"
echo "Built $destination"
