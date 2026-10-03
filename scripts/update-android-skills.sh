#!/usr/bin/env bash
# Vendors the official Android agent skills (github.com/android/skills, Apache 2.0) that this
# project relies on into .claude/skills. Pinned to a commit: bump REF deliberately and review.
#   scripts/update-android-skills.sh [ref]
set -euo pipefail

REF="${1:-42dc2270e96032bd860bb94511e440aa00a43125}"
SKILLS=(
  jetpack-compose/adaptive
  navigation/navigation-3
  system/edge-to-edge
  testing/testing-setup
  security/android-intent-security
)
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git clone --quiet https://github.com/android/skills "$TMP/skills"
git -C "$TMP/skills" checkout --quiet "$REF"
for skill in "${SKILLS[@]}"; do
  name="$(basename "$skill")"
  rm -rf "$ROOT/.claude/skills/$name"
  mkdir -p "$ROOT/.claude/skills"
  cp -R "$TMP/skills/$skill" "$ROOT/.claude/skills/$name"
done
cp "$TMP/skills/LICENSE.txt" "$ROOT/.claude/skills/LICENSE-android-skills.txt"
{
  echo "Official Android agent skills from https://github.com/android/skills"
  echo "Commit: $(git -C "$TMP/skills" rev-parse HEAD) ($(git -C "$TMP/skills" log -1 --format=%cs))"
  echo "Licence: Apache 2.0, see LICENSE-android-skills.txt. Do not edit the copies:"
  echo "update them with scripts/update-android-skills.sh."
} > "$ROOT/.claude/skills/SOURCE.txt"
echo "Vendored: ${SKILLS[*]}"
