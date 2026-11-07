#!/usr/bin/env bash
set -euo pipefail
cp app/build/outputs/apk/debug/app-arm64-v8a-debug.apk frame-1.0.0-preview.apk
sha256sum frame-1.0.0-preview.apk > SHA256SUMS.txt
git -C .. archive --format=tar.gz --output=frame-android/frame-source.tar.gz "$GITHUB_SHA" frame-android .github/workflows/build-frame.yml
git clone --depth 1 --branch 2026.1103.1743 --recurse-submodules --shallow-submodules https://github.com/uBlockOrigin/uBOL-home.git upstream/source
tar --exclude=.git -czf ubOLite-corresponding-source.tar.gz -C upstream source
zip -j frame-screenshots.zip screenshots/frame-*.png
gh release create frame-v1.0.0 frame-1.0.0-preview.apk SHA256SUMS.txt build-manifest.json frame-source.tar.gz ubOLite-corresponding-source.tar.gz upstream/*.xpi frame-screenshots.zip --target "$GITHUB_SHA" --prerelease --title 'Frame 1.0.0 Preview — Website app with uBlock Origin Lite' --notes-file RELEASE.md
