#!/usr/bin/env bash
# Builds app/src/main/assets/gallery_dl.zip: pure-Python gallery-dl with its dependencies.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
python3 -m pip install --quiet --no-compile --target "$WORK/site" \
    --python-version 3.12 --implementation py --abi none --platform any --only-binary=:all: \
    gallery-dl requests urllib3 idna certifi charset-normalizer
rm -rf "${WORK:?}/site/bin" "${WORK:?}"/site/*.dist-info
mkdir -p "$ROOT/app/src/main/assets"
rm -f "${ROOT:?}/app/src/main/assets/gallery_dl.zip"
(cd "$WORK/site" && python3 -m zipfile -c "$ROOT/app/src/main/assets/gallery_dl.zip" .)
