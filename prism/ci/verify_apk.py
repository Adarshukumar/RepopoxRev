#!/usr/bin/env python3
"""
Verify a built PRISM APK.

Gradle reporting success is not the same as the artifact being installable.
This opens the APK and asserts the things that actually matter, then prints
what it found so the build can be confirmed without a device.

Signing is checked properly rather than by looking for META-INF/*.SF. That
file-based scheme is APK Signature Scheme v1, and with minSdk 26 the Android
Gradle Plugin signs with v2 only: the signature lives in the APK Signing
Block, a structure inserted between the ZIP entries and the central directory
that has no ZIP entry of its own. An earlier version of this script looked
only for v1 artifacts and reported a correctly signed APK as unsigned.

Exit status is non-zero on any failure, so CI stops rather than publishing a
broken artifact to a release.
"""

import hashlib
import os
import sys
import zipfile

APK = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk"

REQUIRED_ENTRIES = ["AndroidManifest.xml", "classes.dex", "resources.arsc"]

EOCD_SIG = b"\x50\x4b\x05\x06"
ZIP64_EOCD_LOCATOR_SIG = b"\x50\x4b\x06\x07"
ZIP64_EOCD_SIG = b"\x50\x4b\x06\x06"
SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"

# Signature scheme block IDs, little-endian uint32.
SCHEMES = {
    0x7109871A: "v2",
    0xF05368C0: "v3",
    0x1B93AD61: "v3.1",
    0x42726577: "v4",
}

VALID_ABIS = {
    "armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64", "mips", "mips64",
}

failures = []
notes = []


def check(condition, message):
    if condition:
        print("  ok    " + message)
    else:
        print("  FAIL  " + message)
        failures.append(message)


def note(message):
    notes.append(message)


def central_directory_offset(data):
    """Locate the ZIP central directory, handling the Zip64 form."""
    idx = data.rfind(EOCD_SIG)
    if idx < 0:
        return None, "no end-of-central-directory record"
    offset = int.from_bytes(data[idx + 16: idx + 20], "little")
    if offset != 0xFFFFFFFF:
        return offset, None
    # Zip64: follow the locator to the Zip64 EOCD.
    loc = data.rfind(ZIP64_EOCD_LOCATOR_SIG)
    if loc < 0:
        return None, "offset is 0xFFFFFFFF but there is no Zip64 locator"
    z64 = int.from_bytes(data[loc + 8: loc + 16], "little")
    if data[z64: z64 + 4] != ZIP64_EOCD_SIG:
        return None, "Zip64 locator does not point at a Zip64 EOCD"
    return int.from_bytes(data[z64 + 48: z64 + 56], "little"), None


def inspect_signing(data):
    """Return (signed, schemes, detail) by reading the APK Signing Block."""
    cd, err = central_directory_offset(data)
    if cd is None:
        return False, [], err

    if cd < 24:
        return False, [], "central directory at {} leaves no room for a signing block".format(cd)

    if data[cd - 16: cd] != SIGNING_BLOCK_MAGIC:
        return False, [], (
            "no APK Signing Block immediately before the central directory "
            "(v1-only or unsigned)"
        )

    # The block is: size(8) | pairs | size(8) | magic(16).
    block_size = int.from_bytes(data[cd - 24: cd - 16], "little")
    start = cd - block_size - 8
    if start < 0:
        return False, [], "signing block size {} is larger than the file".format(block_size)

    found = []
    pos = start + 8
    pairs_end = cd - 24
    while pos + 12 <= pairs_end:
        pair_len = int.from_bytes(data[pos: pos + 8], "little")
        if pair_len < 4 or pos + 8 + pair_len > pairs_end:
            break
        block_id = int.from_bytes(data[pos + 8: pos + 12], "little")
        if block_id in SCHEMES:
            found.append(SCHEMES[block_id])
        pos += 8 + pair_len

    detail = "APK Signing Block present ({} bytes), schemes: {}".format(
        block_size, ", ".join(found) if found else "unknown ids"
    )
    return bool(found), found, detail


print("verifying " + APK)

if not os.path.isfile(APK):
    print("  FAIL  apk not found at that path")
    sys.exit(1)

size = os.path.getsize(APK)
with open(APK, "rb") as fh:
    data = fh.read()
digest = hashlib.sha256(data).hexdigest()

print("  size   {:,} bytes".format(size))
print("  sha256 " + digest)
print()

check(zipfile.is_zipfile(APK), "is a valid zip archive")
if not zipfile.is_zipfile(APK):
    print("FAILED: not a zip")
    sys.exit(1)

with zipfile.ZipFile(APK) as zf:
    check(zf.testzip() is None, "zip integrity (no corrupt entry)")
    names = zf.namelist()

print("  entries: {}".format(len(names)))
print()

for entry in REQUIRED_ENTRIES:
    check(entry in names, "contains " + entry)

# ── signing ─────────────────────────────────────────────────────────────
signed, schemes, detail = inspect_signing(data)
check(signed, "signed — " + detail)

# v1 leaves files in META-INF; report them if present but do not require them.
v1 = sorted(n for n in names if n.startswith("META-INF/") and n.endswith((".SF", ".RSA", ".DSA", ".EC")))
if v1:
    note("v1 (JAR) signature files also present: " + ", ".join(v1))

# ── native libraries ────────────────────────────────────────────────────
# Compose pulls in androidx.graphics.path, which ships a prebuilt .so per ABI.
# The app itself declares no native code, so these are expected; what would be
# a defect is one parked under a directory that is not a valid ABI, because
# that means the packaging put it somewhere it can never be loaded from.
native = sorted(n for n in names if n.startswith("lib/") and n.endswith(".so"))
bad_abi = []
for n in native:
    parts = n.split("/")
    if len(parts) != 3 or parts[1] not in VALID_ABIS:
        bad_abi.append(n)
check(not bad_abi, "native libraries sit under valid ABI directories ({})".format(
    ", ".join(bad_abi) if bad_abi else "{} entries".format(len(native))))
if native:
    libs = sorted({n.split("/")[-1] for n in native})
    abis = sorted({n.split("/")[1] for n in native if len(n.split("/")) == 3})
    note("native libs: {} across {}".format(", ".join(libs), ", ".join(abis)))

# ── resources ───────────────────────────────────────────────────────────
mipmaps = [n for n in names if n.startswith("res/") and "mipmap" in n]
check(len(mipmaps) > 0, "launcher icon packaged ({} entries)".format(len(mipmaps)))

res = [n for n in names if n.startswith("res/")]
note("res/ entries: {}".format(len(res)))

dex = sorted(n for n in names if n.endswith(".dex"))
note("dex files: {}".format(", ".join(dex)))

print()
for n in notes:
    print("  note  " + n)

print()
if failures:
    print("FAILED ({}): ".format(len(failures)) + "; ".join(failures))
    sys.exit(1)

print("APK VERIFIED")
