#!/usr/bin/env python3
"""
Verify a built PRISM APK.

Gradle reporting success is not the same as the artifact being correct. This
opens the APK as a zip and asserts the things that actually matter, then
prints a manifest of what it found so a reader can confirm the build without
installing it.

Checks, in order of how badly they would matter if missing:
  1. it is a valid zip and not truncated
  2. AndroidManifest.xml is present  (an APK without one cannot install)
  3. classes.dex is present           (an APK without code is an empty shell)
  4. resources.arsc is present        (compiled resource table)
  5. the adaptive launcher icon made it in
  6. it is signed                     (an unsigned APK will not install)
  7. no native libraries crept in     (this app declares none)

Exit status is non-zero on any failure so CI stops rather than publishing a
broken artifact to a release.
"""

import hashlib
import os
import sys
import zipfile

APK = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk"

REQUIRED = [
    "AndroidManifest.xml",
    "classes.dex",
    "resources.arsc",
]

failures = []
notes = []


def check(condition, message):
    if condition:
        print("  ok    " + message)
    else:
        print("  FAIL  " + message)
        failures.append(message)


print("verifying " + APK)

if not os.path.isfile(APK):
    print("  FAIL  apk not found at that path")
    sys.exit(1)

size = os.path.getsize(APK)
with open(APK, "rb") as fh:
    digest = hashlib.sha256(fh.read()).hexdigest()

print("  size   {:,} bytes".format(size))
print("  sha256 " + digest)
print()

if not zipfile.is_zipfile(APK):
    print("  FAIL  not a valid zip archive")
    sys.exit(1)

with zipfile.ZipFile(APK) as zf:
    bad = zf.testzip()
    check(bad is None, "zip integrity (no corrupt entry)")
    names = zf.namelist()
    print("  entries: {}".format(len(names)))
    print()

    for entry in REQUIRED:
        check(entry in names, "contains " + entry)

    # The launcher icon is an adaptive icon, so it lands under res/ with a
    # hashed name; matching on the mipmap directory is enough to know the
    # resource compiled rather than being silently dropped.
    mipmaps = [n for n in names if "/mipmap" in n or n.startswith("mipmap")]
    check(len(mipmaps) > 0, "adaptive launcher icon present ({} entries)".format(len(mipmaps)))

    signed = [n for n in names if n.startswith("META-INF/") and (
        n.endswith(".RSA") or n.endswith(".DSA") or n.endswith(".EC") or n.endswith(".SF")
    )]
    check(len(signed) > 0, "signed ({})".format(", ".join(sorted(signed)) or "nothing"))

    native = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
    check(len(native) == 0, "no native libraries (expected none, found {})".format(len(native)))

    # Compose ships its own resources; their presence is a decent proxy for
    # the compose artifacts having actually been packaged rather than only
    # compiled against.
    compose = [n for n in names if "compose" in n.lower() or n.startswith("res/") ]
    notes.append("res/ entries: {}".format(len([n for n in names if n.startswith("res/")])))
    notes.append("total entries: {}".format(len(names)))

print()
for n in notes:
    print("  note  " + n)

print()
if failures:
    print("FAILED ({}): ".format(len(failures)) + "; ".join(failures))
    sys.exit(1)

print("APK VERIFIED")
