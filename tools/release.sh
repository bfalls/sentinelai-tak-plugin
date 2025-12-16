#!/usr/bin/env bash
set -euo pipefail

# Usage: tools/release.sh 0.1.1
# Produces tag: v0.1.1

if [[ $# -ne 1 ]]; then
  echo "Usage: tools/release.sh X.Y.Z  (example: tools/release.sh 0.1.1)"
  exit 1
fi

VER="$1"
TAG="v$VER"
FILE="app/build.gradle.kts"
PLUGIN_XML="app/src/main/assets/plugin.xml"

[[ -f "$PLUGIN_XML" ]] || { echo "plugin.xml missing, fix this"; exit 1; }

if [[ ! "$VER" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must look like X.Y.Z (example: 0.1.1)"
  exit 1
fi

# Preconditions
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree not clean. Commit or stash first."
  exit 1
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$BRANCH" != "main" ]]; then
  echo "Not on main (currently $BRANCH)."
  exit 1
fi

git pull --ff-only

# Fail if tag already exists locally or remotely
if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Tag already exists locally: $TAG"
  exit 1
fi
if git ls-remote --tags origin "$TAG" | grep -q "$TAG"; then
  echo "Tag already exists on origin: $TAG"
  exit 1
fi

# Read current versionCode
CURRENT_CODE="$(perl -ne 'print $1 if /versionCode\s*=\s*(\d+)/' "$FILE" | head -n 1)"
if [[ -z "$CURRENT_CODE" ]]; then
  echo "Could not find versionCode in $FILE"
  exit 1
fi
NEXT_CODE=$((CURRENT_CODE + 1))

echo "Updating:"
echo "  versionName -> $VER"
echo "  versionCode -> $NEXT_CODE"

# Update versionName + versionCode (in-place)
perl -0777 -i -pe "s/versionName\s*=\s*\"[^\"]+\"/versionName = \"$VER\"/g; s/versionCode\s*=\s*\d+/versionCode = $NEXT_CODE/g" "$FILE"

# Update plugin.xml version (if present)
if [[ -f "$PLUGIN_XML" ]]; then
  perl -0777 -i -pe "s/(<plugin[^>]*version=\")([^\"]+)(\")/\$1$VER\$3/" "$PLUGIN_XML"
  echo "  plugin.xml version -> $VER"
else
  echo "WARNING: $PLUGIN_XML not found; plugin.xml version not updated"
fi

# Optional: local preflight build (recommended)
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --no-daemon

# Commit + push
git add "$FILE" "$PLUGIN_XML"
git commit -m "Bump version to $VER"
git push origin main

# Tag + push tag (triggers GitHub Release workflow)
git tag -a "$TAG" -m "$TAG"
git push origin "$TAG"

echo "Done. GitHub Actions should build and publish the release for $TAG."
