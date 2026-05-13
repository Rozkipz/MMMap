#!/usr/bin/env bash
# Bumps versionName and versionCode in app/build.gradle.kts
# Usage: bump-version.sh [patch|minor|major]
set -euo pipefail

PART="${1:-patch}"
BUILD_FILE="app/build.gradle.kts"

# Extract current values
CURRENT_NAME=$(grep 'versionName' "$BUILD_FILE" | grep -oP '"\K[^"]+')
CURRENT_CODE=$(grep 'versionCode' "$BUILD_FILE" | grep -oP '\d+')

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_NAME"

case "$PART" in
    major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
    minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
    patch) PATCH=$((PATCH + 1)) ;;
    *) echo "Usage: bump-version.sh [patch|minor|major]"; exit 1 ;;
esac

NEW_NAME="${MAJOR}.${MINOR}.${PATCH}"
NEW_CODE=$((CURRENT_CODE + 1))

sed -i "s/versionName = \"${CURRENT_NAME}\"/versionName = \"${NEW_NAME}\"/" "$BUILD_FILE"
sed -i "s/versionCode = ${CURRENT_CODE}/versionCode = ${NEW_CODE}/" "$BUILD_FILE"
echo "$NEW_CODE" > .version-code

echo "Bumped: ${CURRENT_NAME} (${CURRENT_CODE}) → ${NEW_NAME} (${NEW_CODE})"
