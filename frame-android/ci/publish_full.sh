#!/usr/bin/env bash
set -euo pipefail
cp app/build/outputs/apk/debug/app-arm64-v8a-debug.apk frame-youtube-1.1.0-preview.apk
sha256sum frame-youtube-1.1.0-preview.apk > SHA256SUMS.txt
git -C .. archive --format=tar.gz --output=frame-android/frame-source.tar.gz "$GITHUB_SHA" frame-android .github/workflows/build-frame-full.yml .github/workflows/build-frame.yml
git clone --depth 1 --branch 1.75.4 --recurse-submodules --shallow-submodules https://github.com/gorhill/uBlock.git upstream/full-source
tar --exclude=.git -czf ublock-corresponding-source.tar.gz -C upstream full-source
zip -j frame-screenshots.zip screenshots/frame-*.png
cp FULL-UBO-EXPERIMENT.md full-release-notes.md
printf '\n## Actual external-site test result\n\n' >> full-release-notes.md
cat screenshots/youtube-result.txt >> full-release-notes.md
gh release create frame-youtube-v1.1.0 frame-youtube-1.1.0-preview.apk SHA256SUMS.txt build-manifest.json frame-source.tar.gz ublock-corresponding-source.tar.gz upstream/*.xpi frame-screenshots.zip screenshots/youtube-result.txt screenshots/blocking-evidence.json screenshots/youtube-playback-evidence.json --target "$GITHUB_SHA" --prerelease --title 'Frame YouTube Preview — full uBlock Origin' --notes-file full-release-notes.md
