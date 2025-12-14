#!/usr/bin/env bash
set -euo pipefail

# Zips only tracked text files (skips binaries like png/jar/apk/etc)
# Output: dist/sentinelai-tak-plugin-src-text-YYYYmmdd-HHMMSS.zip

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v git >/dev/null 2>&1; then
  echo "ERROR: git not found"
  exit 1
fi

if ! command -v zip >/dev/null 2>&1; then
  echo "ERROR: zip not found"
  exit 1
fi

mkdir -p dist

TS="$(date +%Y%m%d-%H%M%S)"
OUT="./sentinelai-tak-plugin-src-text-${TS}.zip"

# Helper: return 0 if file is text, 1 if binary
is_text_file() {
  local f="$1"

  # Skip obvious binary extensions quickly
  case "${f,,}" in
    *.png|*.jpg|*.jpeg|*.gif|*.webp|*.ico|*.icns|*.pdf|*.zip|*.jar|*.aar|*.apk|*.aab|*.keystore|*.jks|*.so|*.dylib|*.dll|*.class)
      return 1
      ;;
  esac

  # Use `file` if available (best signal on macOS)
  if command -v file >/dev/null 2>&1; then
    # If file says binary charset, treat as binary
    if file -b --mime "$f" | grep -qi 'charset=binary'; then
      return 1
    fi
    return 0
  fi

  # Fallback: treat NUL bytes as binary
  if LC_ALL=C grep -qU $'\x00' "$f"; then
    return 1
  fi

  return 0
}

# Create zip fresh
rm -f "$OUT"

# Loop through tracked files only
while IFS= read -r f; do
  # Skip missing (rare, but safe)
  [[ -f "$f" ]] || continue

  if is_text_file "$f"; then
    zip -q "$OUT" "$f"
  fi
done < <(git ls-files)

echo "Wrote: $OUT"
