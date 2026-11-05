#!/usr/bin/env bash
# Refreshes third_party/python: pure-Python gallery-dl with its dependencies. The build zips it into the APK.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "${ROOT:?}/third_party/python"
python3 -m pip install --quiet --no-compile --target "$ROOT/third_party/python" \
    --python-version 3.12 --implementation py --abi none --platform any --only-binary=:all: \
    gallery-dl requests urllib3 idna certifi charset-normalizer
rm -rf "${ROOT:?}/third_party/python/bin" "${ROOT:?}"/third_party/python/*.dist-info
