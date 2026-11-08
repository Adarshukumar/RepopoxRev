#!/usr/bin/env bash
set -euo pipefail
cp app/build/outputs/apk/debug/app-arm64-v8a-debug.apk frame-full-ubo-experiment.apk
sha256sum frame-full-ubo-experiment.apk > SHA256SUMS.txt
git -C .. archive --format=tar.gz --output=frame-android/frame-source.tar.gz "$GITHUB_SHA" frame-android .github/workflows/build-frame-full.yml .github/workflows/build-frame.yml
git clone --depth 1 --branch 1.75.4 --recurse-submodules --shallow-submodules https://github.com/gorhill/uBlock.git upstream/full-source
tar --exclude=.git -czf ublock-corresponding-source.tar.gz -C upstream full-source
zip -j frame-screenshots.zip screenshots/frame-*.png
cp FULL-UBO-EXPERIMENT.md full-release-notes.md
printf '\n## Actual external-site test result\n\n' >> full-release-notes.md
cat screenshots/youtube-result.txt >> full-release-notes.md
gh release create frame-full-ubo-experiment-1 frame-full-ubo-experiment.apk SHA256SUMS.txt build-manifest.json frame-source.tar.gz ublock-corresponding-source.tar.gz upstream/*.xpi frame-screenshots.zip screenshots/youtube-result.txt --target "$GITHUB_SHA" --prerelease --title 'Frame — full uBlock Origin experiment' --notes-file full-release-notes.md
