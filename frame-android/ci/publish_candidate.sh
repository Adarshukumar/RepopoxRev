#!/usr/bin/env bash
# Explicit manual-device candidate: never marks the failed playback check passed.
set -euo pipefail
[[ "$SOURCE_RUN" =~ ^[0-9]+$ ]]
gh run view "$SOURCE_RUN" --json headSha,headBranch,workflowName,jobs,url > candidate-run.json
python3 - <<'PY'
import json,re
r=json.load(open('candidate-run.json'))
assert r['headBranch']=='arena/c4a5f7e4-repopoxrev'
assert r['workflowName']=='Frame full uBlock Origin experiment'
assert re.fullmatch('[a-f0-9]{40}',r['headSha'])
steps={s['name']:s['conclusion'] for j in r['jobs'] for s in j['steps']}
for name in ['Compile native app and test APK','Domain unit tests','Verify APK structure and signature block']:
    assert steps[name]=='success', (name,steps.get(name))
open('candidate-sha.txt','w').write(r['headSha'])
PY
SOURCE_SHA=$(cat candidate-sha.txt)
git merge-base --is-ancestor "$SOURCE_SHA" HEAD
gh run download "$SOURCE_RUN" --name frame-full-ubo-experiment --dir candidate
python3 - <<'PY'
import json,pathlib,xml.etree.ElementTree as E
p=pathlib.Path('candidate/screenshots')
passed=False
for f in (p/'core-test-results').rglob('*.xml'):
    tree=E.parse(f)
    for case in tree.iter('testcase'):
        if case.get('name')=='realExtensionBlocksAndDashboardWorks':
            assert case.find('failure') is None and case.find('error') is None and case.find('skipped') is None
            passed=True
assert passed,'Missing passing controlled integration test'
b=json.loads((p/'blocking-evidence.json').read_text())
assert b['extension']=='uBlock Origin'
assert b['enabledAdRequests']==0 and b['disabledAdRequests']>0 and b['reenabledAdditionalAdRequests']==0
v=json.loads((p/'youtube-playback-evidence.json').read_text())
assert any('not a bot' in s.get('pageText','').lower() for s in v['samples']), 'Candidate exception is only for a documented YouTube challenge'
manifest=json.load(open('candidate/build-manifest.json'))
assert manifest['sha256']=='e2df503cce7cf5069130618c349695f62bdddb9eb8c189156d027269ae4ae267'
assert manifest['id']=='uBlock0@raymondhill.net'
print('Verified controlled blocking; confirmed external YouTube challenge. Playback NOT marked passing.')
PY
cp candidate/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk frame-youtube-1.1.0-device-preview.apk
python3 ci/verify_apk.py frame-youtube-1.1.0-device-preview.apk
SIGNER=$(find "$ANDROID_HOME/build-tools" -name apksigner | sort -V | tail -1)
"$SIGNER" verify --verbose frame-youtube-1.1.0-device-preview.apk
mkdir -p candidate-upstream
gh release download 1.75.4 --repo gorhill/uBlock --pattern '*.firefox.signed.xpi' --dir candidate-upstream
python3 - <<'PY'
import pathlib,zipfile,hashlib
x=next(pathlib.Path('candidate-upstream').glob('*.xpi'))
assert hashlib.sha256(x.read_bytes()).hexdigest()=='e2df503cce7cf5069130618c349695f62bdddb9eb8c189156d027269ae4ae267'
with zipfile.ZipFile(x) as upstream,zipfile.ZipFile('frame-youtube-1.1.0-device-preview.apk') as apk:
    for name in upstream.namelist():
        if not name.endswith('/'):
            assert upstream.read(name)==apk.read('assets/ubol/'+name),name
print('Original full uBO preserved byte-for-byte in the candidate APK')
PY
sha256sum frame-youtube-1.1.0-device-preview.apk > SHA256SUMS.txt
git -C .. archive --format=tar.gz --output=frame-android/frame-source.tar.gz "$SOURCE_SHA" frame-android .github/workflows/build-frame-full.yml .github/workflows/build-frame.yml
git clone --depth 1 --branch 1.75.4 --recurse-submodules --shallow-submodules https://github.com/gorhill/uBlock.git candidate-upstream/source
tar --exclude=.git -czf ublock-corresponding-source.tar.gz -C candidate-upstream source
zip -j frame-screenshots.zip candidate/screenshots/frame-*.png
cp DEVICE-PREVIEW.md device-release-notes.md
printf '\nTested source: `%s`. [Original test run](https://github.com/%s/actions/runs/%s).\n' "$SOURCE_SHA" "$GITHUB_REPOSITORY" "$SOURCE_RUN" >> device-release-notes.md
printf '\nAPK SHA256:\n```\n' >> device-release-notes.md
cat SHA256SUMS.txt >> device-release-notes.md
printf '```\n' >> device-release-notes.md
gh release create frame-youtube-v1.1.0-device-preview frame-youtube-1.1.0-device-preview.apk SHA256SUMS.txt candidate/build-manifest.json frame-source.tar.gz ublock-corresponding-source.tar.gz candidate-upstream/*.xpi frame-screenshots.zip candidate/screenshots/blocking-evidence.json candidate/screenshots/youtube-playback-evidence.json candidate/screenshots/youtube-result.txt --target "$SOURCE_SHA" --prerelease --title 'Frame YouTube 1.1 — device preview, full uBlock Origin' --notes-file device-release-notes.md
