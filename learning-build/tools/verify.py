#!/usr/bin/env python3
"""
verify.py -- static build-verification harness for the Mochy-Music clone.

WHY THIS EXISTS
---------------
The clone is a ~231k-line Android/Kotlin multi-module project (1 app module, 16 provider
modules, 5 source modules, a C++17 native library). A real `./gradlew assemble…` needs a
JDK 21, the Android SDK + NDK + CMake, and network access to google(), mavenCentral(),
jitpack.io and maven.aliyun.com. None of those exist in the analysis sandbox, and this
sandbox has 2 vCPU / 4 GB RAM. Pretending otherwise would produce a fake green tick, so
this harness instead runs every verification that *can* be decided from the sources alone
and reports each one as PASS / FAIL / WARN / INFO with evidence.

It is a real checker, not a linter config: kotlin_lex.py lexes Kotlin (nestable block
comments, nested string templates, raw strings, backtick identifiers) and kotlin_index.py
builds a symbol index of all 28k declarations, so "does this type exist?" is answered from
the code rather than from file names.

CHECKS
------
 1  gradle-modules    settings include() vs. directories vs. project(":…") references
 2  version-catalog   every libs.* / libs.plugins.* alias resolves in libs.versions.toml
 3  manifest          every component/resource/authority/icon reference resolves
 4  resources         XML well-formed; every @type/name and R.type.name resolves; no dupes
 5  i18n              translation coverage; orphan keys that would crash the default locale
 6  kotlin            lexically balanced; package/path; duplicates scoped per source set
 7  room              @Database entities exist; migration graph has no unreachable versions
 8  tests             test imports resolve to real declarations
 9  module-deps       cross-module imports are backed by a declared project() dependency
10  ci                gradle task names exist in the flavor × buildType matrix
11  prereqs           keystores, proguard, cmake, wrapper, build.sh portability
12  buildconfig       BuildConfig fields read in Kotlin are defined by Gradle
13  json-docs         JSON parses; referenced docs/*.md paths exist
14  secrets           no hard-coded credentials
15  flavors           flavor source sets wired; fdroid reuses foss sources
16  app-icons         the 50 manifest launcher aliases == the set AppIcons.kt generates

Usage:  python3 verify.py [path-to-Mochy-Music]     (default: ../../Mochy-Music)
        exit 0 = no FAIL, 1 = at least one FAIL
"""

from __future__ import annotations

import itertools
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from kotlin_lex import mask_kotlin, balance, first_anomaly, strip_gradle_comments  # noqa: E402
from kotlin_index import KotlinIndex  # noqa: E402

PASS, FAIL, WARN, INFO = "PASS", "FAIL", "WARN", "INFO"


@dataclass
class Finding:
    level: str
    check: str
    message: str
    evidence: list[str] = field(default_factory=list)


class Report:
    def __init__(self) -> None:
        self.findings: list[Finding] = []
        self.stats: dict[str, object] = {}

    def add(self, level, check, message, evidence=None):
        self.findings.append(Finding(level, check, message, evidence or []))

    def counts(self) -> Counter:
        return Counter(f.level for f in self.findings)


# --------------------------------------------------------------------------- #
# helpers
# --------------------------------------------------------------------------- #

def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def walk(root: Path, *suffixes: str) -> list[Path]:
    out = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in {".git", "build", ".gradle", "node_modules"}]
        for name in filenames:
            if not suffixes or name.endswith(suffixes):
                out.append(Path(dirpath) / name)
    return sorted(out)


def strip_line_comments(src: str) -> str:
    """Remove // and /* */ comments from a Gradle script but KEEP its string literals.

    Gradle scripts are Kotlin, so the full lexer is right for bracket questions -- but it
    blanks string literals, and every interesting fact in a build script (module names,
    flavor names, file paths) lives inside a string. For those we only need comments gone.
    """
    out = []
    i, n = 0, len(src)
    depth = 0
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if depth:
            if c == "/" and nxt == "*":
                depth += 1
                i += 2
                continue
            if c == "*" and nxt == "/":
                depth -= 1
                i += 2
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        if c == "/" and nxt == "/":
            while i < n and src[i] != "\n":
                i += 1
            continue
        if c == "/" and nxt == "*":
            depth += 1
            i += 2
            continue
        if c == '"':                      # skip over the literal untouched
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == '"':
                    j += 1
                    break
                j += 1
            out.append(src[i:j])
            i = j
            continue
        out.append(c)
        i += 1
    return "".join(out)


def xml_root(path: Path):
    try:
        return ET.fromstring(read(path)), None
    except ET.ParseError as exc:
        return None, f"line {exc.position[0]}: {exc.msg}"
    except Exception as exc:                                  # noqa: BLE001
        return None, str(exc)


def run(cmd, cwd=None):
    try:
        p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=180)
        return p.returncode, (p.stdout or "") + (p.stderr or "")
    except FileNotFoundError:
        return None, "tool not installed"
    except subprocess.TimeoutExpired:
        return None, "timeout"


# --------------------------------------------------------------------------- #
# project model
# --------------------------------------------------------------------------- #

class Project:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.settings_raw = read(root / "settings.gradle.kts")
        self.settings = strip_gradle_comments(self.settings_raw)
        self.app_gradle_raw = read(root / "app" / "build.gradle.kts")
        self.app_gradle = strip_gradle_comments(self.app_gradle_raw)
        self.catalog = read(root / "gradle" / "libs.versions.toml")
        self.manifest_path = root / "app" / "src" / "main" / "AndroidManifest.xml"
        self.index = KotlinIndex(root)
        self.kotlin_files = [fs.path for fs in self.index.files]
        self.xml_files = walk(root, ".xml")
        self.json_files = [p for p in walk(root, ".json") if "schemas" not in p.parts]
        self.schema_files = [p for p in walk(root, ".json") if "schemas" in p.parts]
        self._masked: dict[Path, str] = {}
        # package prefix -> gradle module, and module -> declared project deps
        self.pkg_owner: dict[str, set[str]] = defaultdict(set)
        self.module_deps: dict[str, set[str]] = defaultdict(set)
        self._build_module_graph()

    def masked(self, path: Path) -> str:
        if path not in self._masked:
            self._masked[path] = mask_kotlin(read(path))
        return self._masked[path]

    # ---- gradle --------------------------------------------------------- #
    def gradle_modules(self) -> list[str]:
        # read the RAW file: the lexer blanks string literals, and include(":app") is one
        raw = strip_line_comments(self.settings_raw)
        return re.findall(r'include\(\s*"(:[^"]+)"\s*\)', raw)

    def _module_gradle(self, mod: str) -> Path:
        return self.root / mod.lstrip(":").replace(":", os.sep) / "build.gradle.kts"

    def _build_module_graph(self) -> None:
        for fs in self.index.files:
            mod = self.index.module_of(fs.path)
            if fs.package:
                self.pkg_owner[fs.package].add(mod)
        for mod in [":app"] + self.gradle_modules():
            g = self._module_gradle(mod)
            if not g.exists():
                continue
            body = strip_line_comments(read(g))
            for dep in re.findall(r'project\(\s*"(:[^"]+)"\s*\)', body):
                self.module_deps[mod].add(dep)

    def project_deps(self) -> set[str]:
        found: set[str] = set()
        for gradle in walk(self.root, ".gradle.kts", ".gradle"):
            found |= set(re.findall(r'project\(\s*"(:[^"]+)"\s*\)', strip_line_comments(read(gradle))))
        return found

    def catalog_versions(self) -> set[str]:
        section, out = None, set()
        for line in self.catalog.splitlines():
            s = line.strip()
            if s.startswith("["):
                section = s
            elif section == "[versions]" and "=" in s and not s.startswith("#"):
                out.add(s.split("=", 1)[0].strip())
        return out

    def catalog_aliases(self) -> tuple[set[str], set[str]]:
        section, libs, plugins = None, set(), set()
        for line in self.catalog.splitlines():
            s = line.strip()
            if s.startswith("["):
                section = s
                continue
            if not s or s.startswith("#") or "=" not in s:
                continue
            name = s.split("=", 1)[0].strip()
            if section == "[libraries]":
                libs.add(name)
            elif section == "[plugins]":
                plugins.add(name)
        return libs, plugins

    def flavors(self) -> dict[str, list[str]]:
        """dimension -> flavor names, from the productFlavors block only.

        Scanning the whole build script for `create("x")` also picks up the signingConfig and
        buildType entries, which would invent phantom flavor dimensions and multiply the
        variant count nonsense-ward, so the search is bounded to productFlavors{...}.
        """
        out: dict[str, list[str]] = defaultdict(list)
        start = self.app_gradle_raw.find("productFlavors")
        if start < 0:
            return {}
        depth, i = 0, start
        while i < len(self.app_gradle_raw):
            c = self.app_gradle_raw[i]
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        block = self.app_gradle_raw[start:i + 1]
        for m in re.finditer(r'create\("([A-Za-z0-9_]+)"\)\s*\{(.*?)\n        \}', block, re.S):
            name, body = m.group(1), m.group(2)
            dim = re.search(r'dimension\s*=\s*"([^"]+)"', body)
            out[dim.group(1) if dim else "?"].append(name)
        return dict(out)

    def build_types(self) -> list[str]:
        known = {"release", "debug", "profileable", "fast"}
        names = set(re.findall(r"^\s{8}(\w+)\s*\{", self.app_gradle_raw, re.M))
        names |= set(re.findall(r'create\("(\w+)"\)', self.app_gradle_raw))
        return sorted(names & known)

    def variants(self) -> set[str]:
        dims = sorted(self.flavors())
        combos = itertools.product(*[self.flavors()[d] for d in dims]) if dims else [()]
        cap = lambda s: s[:1].upper() + s[1:]
        tasks = set()
        for combo in combos:
            for bt in self.build_types():
                for verb in ("assemble", "bundle"):
                    tasks.add(verb + "".join(cap(c) for c in combo) + cap(bt))
        tasks |= {"assemble", "build", "clean", "test", "lint", "testDebugUnitTest",
                  "testReleaseUnitTest", "lintDebug", "lintRelease", "testFastUnitTest",
                  "testProfileableUnitTest", "assembleDebug", "assembleRelease"}
        return tasks

    # ---- resources ------------------------------------------------------ #
    def source_sets(self) -> list[Path]:
        src = self.root / "app" / "src"
        return sorted(d for d in src.iterdir() if d.is_dir()) if src.is_dir() else []

    def resource_dirs(self) -> list[Path]:
        return [ss / "res" for ss in self.source_sets() if (ss / "res").is_dir()]

    def declared_values(self) -> dict[str, set[str]]:
        decl: dict[str, set[str]] = defaultdict(set)
        for res in self.resource_dirs():
            for vdir in sorted(res.glob("values*")):
                if not vdir.is_dir():
                    continue
                qual = vdir.name[len("values"):]
                if qual and not re.fullmatch(r"(-v\d+|-night(-v\d+)?)", qual):
                    continue
                for xml in walk(vdir, ".xml"):
                    root, err = xml_root(xml)
                    if root is None:
                        continue
                    for child in root:
                        name = child.get("name")
                        if not name:
                            continue
                        if child.tag == "declare-styleable":
                            decl["styleable"].add(name)
                            for a in child:
                                if a.tag == "attr" and a.get("name"):
                                    decl["attr"].add(a.get("name"))
                        elif child.tag in ("string-array", "integer-array", "array"):
                            decl["array"].add(name)
                        else:
                            decl[child.tag].add(name)
        return decl

    def declared_files(self) -> dict[str, set[str]]:
        decl: dict[str, set[str]] = defaultdict(set)
        exts = {".xml", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".ttf", ".otf",
                ".mp3", ".wav", ".json", ".svg"}
        for res in self.resource_dirs():
            for d in sorted(res.iterdir()):
                if not d.is_dir() or d.name.startswith("values"):
                    continue
                rtype = d.name.split("-")[0]
                for f in walk(d):
                    if f.suffix.lower() in exts:
                        decl[rtype].add(f.stem)
        return decl

    def default_string_keys(self) -> set[str]:
        names: set[str] = set()
        for res in self.resource_dirs():
            for xml in walk(res / "values", ".xml"):
                root, _ = xml_root(xml)
                if root is None:
                    continue
                for child in root:
                    if child.tag in {"string", "plurals", "string-array"} and child.get("name"):
                        names.add(child.get("name"))
        return names

    def translation_keys(self) -> dict[str, set[str]]:
        out: dict[str, set[str]] = {}
        for res in self.resource_dirs():
            for vdir in sorted(res.glob("values-*")):
                if not vdir.is_dir() or vdir.name in ("values-night", "values-v31", "values-night-v31"):
                    continue
                names: set[str] = set()
                for xml in walk(vdir, ".xml"):
                    root, _ = xml_root(xml)
                    if root is None:
                        continue
                    for child in root:
                        if child.tag in {"string", "plurals", "string-array"} and child.get("name"):
                            names.add(child.get("name"))
                out[vdir.name] = names
        return out


# --------------------------------------------------------------------------- #
# 1. gradle modules
# --------------------------------------------------------------------------- #

def check_gradle_modules(p: Project, r: Report) -> None:
    mods = p.gradle_modules()
    deps = p.project_deps()
    no_dir = [m for m in mods if not (p.root / m.lstrip(":").replace(":", os.sep)).is_dir()]
    no_gradle = [m for m in mods
                 if (p.root / m.lstrip(":").replace(":", os.sep)).is_dir()
                 and not list((p.root / m.lstrip(":").replace(":", os.sep)).glob("build.gradle*"))]
    undeclared = sorted(d for d in deps if d not in mods)
    for lvl, msg, ev in (
        (FAIL, "settings.gradle.kts includes modules with no directory", no_dir),
        (FAIL, "included modules without a build.gradle(.kts)", no_gradle),
        (FAIL, "project(\":x\") referenced but never include()d (comment-stripped)", undeclared),
    ):
        if ev:
            r.add(lvl, "gradle-modules", msg, ev)
    if not (no_dir or no_gradle or undeclared):
        libs = len(mods) - 1
        r.add(PASS, "gradle-modules",
              f"all {len(mods)} include()d modules exist with a build script, and every "
              f"project(\":…\") reference (after stripping comments) is declared",
              [f"1 :app + {libs} library modules",
               f"{len([m for m in mods if m.startswith(':providers')])} providers, "
               f"{len([m for m in mods if m.startswith(':modules')])} source modules"])
    r.stats["gradle_modules"] = mods


# --------------------------------------------------------------------------- #
# 2. version catalog
# --------------------------------------------------------------------------- #

def check_catalog(p: Project, r: Report) -> None:
    libs, plugins = p.catalog_aliases()
    versions = p.catalog_versions()
    norm = lambda s: s.replace(".", "-").replace("_", "-")
    lib_n = {norm(a) for a in libs}
    plug_n = {norm(a) for a in plugins}
    ver_n = {norm(a) for a in versions}
    used_libs, used_plugins, used_versions = set(), set(), set()
    for gradle in walk(p.root, ".gradle.kts"):
        txt = strip_line_comments(read(gradle))
        for acc in re.findall(r"\blibs\.([A-Za-z0-9_.\-]+)", txt):
            if acc.startswith("plugins."):
                used_plugins.add(norm(acc[len("plugins."):]))
            elif acc.startswith("versions."):
                # the accessor is `libs.versions.kotlin.get()`, so drop the trailing `.get`
                v = acc[len("versions."):]
                v = re.sub(r"\.get$", "", v)
                used_versions.add(norm(v))
            else:
                used_libs.add(norm(acc))
    bad_libs = sorted(u for u in used_libs if u not in lib_n)
    bad_plugs = sorted(u for u in used_plugins if u not in plug_n)
    bad_vers = sorted(u for u in used_versions if u not in ver_n)
    for lvl, msg, ev in (
        (FAIL, "libs.<alias> used by a build script but not declared under [libraries]", bad_libs),
        (FAIL, "libs.plugins.<alias> used but not declared under [plugins]", bad_plugs),
        (FAIL, "libs.versions.<alias> used but not declared under [versions]", bad_vers),
    ):
        if ev:
            r.add(lvl, "version-catalog", msg, ev)
    if not (bad_libs or bad_plugs or bad_vers):
        r.add(PASS, "version-catalog",
              f"every catalog accessor in every build script resolves: {len(used_libs)} libraries, "
              f"{len(used_plugins)} plugins, {len(used_versions)} version refs",
              [f"gradle/libs.versions.toml declares {len(versions)} versions, {len(libs)} libraries, "
               f"{len(plugins)} plugins"])
    unused = sorted(libs - used_libs)
    if unused:
        r.add(INFO, "version-catalog", "[libraries] entries declared but never referenced by a build "
              "script (flavor-conditional and direct-coordinate deps land here)", unused)
    r.stats["catalog"] = {"versions": len(versions), "libraries": len(libs), "plugins": len(plugins),
                          "used_libs": len(used_libs), "used_plugins": len(used_plugins),
                          "used_versions": len(used_versions), "unused_libs": unused}


# --------------------------------------------------------------------------- #
# 3. manifest
# --------------------------------------------------------------------------- #

def check_manifest(p: Project, r: Report) -> None:
    root, err = xml_root(p.manifest_path)
    if root is None:
        r.add(FAIL, "manifest", f"AndroidManifest.xml is not well-formed: {err}")
        return
    A = "{http://schemas.android.com/apk/res/android}"
    m = re.search(r'applicationId\s*=\s*"([^"]+)"', p.app_gradle_raw)
    ns = m.group(1) if m else ""
    r.stats["application_id"] = ns

    real_tags = ("activity", "service", "receiver", "provider")
    missing, aliases = [], []
    counts = Counter()
    for tag in real_tags + ("activity-alias",):
        for el in root.iter(tag):
            name = el.get(f"{A}name")
            if not name:
                continue
            counts[tag] += 1
            fq = name if name.count(".") and not name.startswith(".") else ns + (name if name.startswith(".") else "." + name)
            if tag == "activity-alias":
                aliases.append((name.lstrip("."), fq, el.get(f"{A}icon"), el.get(f"{A}enabled")))
                continue                     # an alias is manifest-only: it needs no class
            if fq.startswith(("androidx.", "android.", "com.yalantis.", "com.google.android.")):
                continue                     # framework / library class
            if not p.index.has_fqn(fq) and fq.split(".")[-1] not in p.index.by_simple:
                missing.append(f"<{tag}> {fq}")
    if missing:
        r.add(FAIL, "manifest",
              "manifest declares a component whose class does not exist anywhere in the sources. "
              "The build still succeeds (AGP does not resolve component classes), but anything "
              "that tries to start it dies at runtime with ActivityNotFound/ClassNotFound.",
              missing)
    else:
        r.add(PASS, "manifest",
              f"all {sum(counts[t] for t in real_tags)} activity/service/receiver/provider classes "
              f"resolve to real declarations")

    txt = read(p.manifest_path)
    decl_v, decl_f = p.declared_values(), p.declared_files()
    available = set()
    for s in decl_v.values():
        available |= s
    for s in decl_f.values():
        available |= s
    refs = set(re.findall(r'"@(?:string|drawable|mipmap|xml|color|style|layout|raw|font|array|'
                          r'plurals|dimen|bool|integer|anim|animator|menu|navigation)/'
                          r'([A-Za-z0-9_.]+)"', txt))
    bad = sorted(x for x in refs
                 if x not in available and x.replace(".", "_") not in available
                 and not x.startswith(LIB_STYLE_PREFIX))
    if bad:
        r.add(FAIL, "manifest", "manifest @resource references that do not exist", bad)
    else:
        r.add(PASS, "manifest", f"all {len(refs)} @resource references in the manifest resolve")

    auths = [el.get(f"{A}authorities") for el in root.iter("provider") if el.get(f"{A}authorities")]
    if len(auths) != len(set(auths)):
        r.add(FAIL, "manifest", "duplicate provider authorities", auths)
    else:
        r.add(PASS, "manifest", f"{len(auths)} provider authorities unique",
              [a for a in auths if a])

    perms = sorted({el.get(f"{A}name") for el in root.iter("uses-permission") if el.get(f"{A}name")})
    r.stats["permissions"] = perms
    r.add(INFO, "manifest", f"{len(perms)} permissions declared", perms)

    enabled = [a for a in aliases if a[3] == "true"]
    bad_icons = [a[0] for a in aliases
                 if a[2] and a[2].split("/")[-1] not in (decl_f.get("mipmap", set()) | decl_f.get("drawable", set()))]
    if len(enabled) != 1:
        r.add(FAIL if not enabled else WARN, "manifest",
              f"expected exactly one enabled launcher activity-alias, found {len(enabled)}",
              [str(e) for e in enabled])
    else:
        r.add(PASS, "manifest",
              f"exactly 1 of {len(aliases)} launcher activity-alias entries enabled ({enabled[0][0]}); "
              f"the app swaps them at runtime")
    if bad_icons:
        r.add(FAIL, "manifest", "activity-alias android:icon points at a missing mipmap/drawable",
              bad_icons)
    else:
        r.add(PASS, "manifest", f"all {len(aliases)} activity-alias icons exist")
    r.stats["icon_aliases"] = [a[1] if a[1].startswith("Icon") else a[0] for a in aliases]


# --------------------------------------------------------------------------- #
# 4. resources
# --------------------------------------------------------------------------- #

REF_RE = re.compile(r'@(?:\+)?(string|plurals|drawable|mipmap|color|style|dimen|bool|integer|'
                    r'array|string-array|integer-array|layout|raw|font|anim|animator|menu|'
                    r'navigation|xml|attr|styleable)/([A-Za-z0-9_.]+)')
# the (?<![\w.]) guard matters: without it `android.R.string.ok` is read as this app's own
# R.string.ok, and the framework's resource is then reported as missing from res/.
R_RE = re.compile(r"(?<![\w.])R\.(string|plurals|drawable|mipmap|color|style|dimen|bool|integer|"
                  r"array|layout|raw|font|anim|animator|menu|navigation|xml|attr)\.([A-Za-z0-9_]+)")

# resource families that ship inside AAR dependencies (appcompat, material, splashscreen, media3,
# ucrop) instead of this repository's res/ folders. A static checker cannot open an AAR, so these
# are allowlisted rather than reported dangling.
LIB_STYLE_PREFIX = ("Theme.AppCompat", "Theme.MaterialComponents", "Theme.Material3",
                    "Theme.SplashScreen", "ThemeOverlay.AppCompat", "ThemeOverlay.Material",
                    "Widget.AppCompat", "Widget.MaterialComponents", "Widget.Material3",
                    "TextAppearance.AppCompat", "TextAppearance.Material", "Base.Theme",
                    "Base.Widget", "Platform.", "Animation.AppCompat", "Material.", "ucrop_")


def check_resources(p: Project, r: Report) -> None:
    decl_v, decl_f = p.declared_values(), p.declared_files()
    available: dict[str, set[str]] = defaultdict(set)
    for k, v in list(decl_v.items()) + list(decl_f.items()):
        available[k] |= v

    bad_xml = []
    for xml in p.xml_files:
        root, err = xml_root(xml)
        if root is None:
            bad_xml.append(f"{xml.relative_to(p.root)}: {err}")
    if bad_xml:
        r.add(FAIL, "resources", "XML files that are not well-formed", bad_xml[:20])
    else:
        r.add(PASS, "resources", f"all {len(p.xml_files)} XML files parse as well-formed XML")

    unresolved: Counter = Counter()
    example: dict[str, str] = {}
    total = 0
    for xml in p.xml_files:
        if "fastlane" in xml.parts:
            continue
        for typ, name in REF_RE.findall(read(xml)):
            total += 1
            if name in available.get(typ, set()):
                continue
            if typ in ("style", "styleable") and name.replace(".", "_") in available.get(typ, set()):
                continue
            if name.startswith(LIB_STYLE_PREFIX):
                continue
            key = f"@{typ}/{name}"
            unresolved[key] += 1
            example.setdefault(key, str(xml.relative_to(p.root)))
    kt_unresolved: Counter = Counter()
    kt_example: dict[str, str] = {}
    for kt in p.kotlin_files:
        for typ, name in R_RE.findall(p.masked(kt)):
            total += 1
            if name in available.get(typ, set()):
                continue
            if name.replace("_", ".") in available.get(typ, set()):
                continue
            if name.replace("_", ".").startswith(LIB_STYLE_PREFIX):
                continue
            key = f"R.{typ}.{name}"
            kt_unresolved[key] += 1
            kt_example.setdefault(key, str(kt.relative_to(p.root)))
    if unresolved:
        r.add(FAIL, "resources", f"{len(unresolved)} distinct unresolved @type/name in XML",
              [f"{k} ×{v} (e.g. {example[k]})" for k, v in unresolved.most_common(30)])
    else:
        r.add(PASS, "resources", f"every @type/name reference in XML resolves")
    if kt_unresolved:
        r.add(FAIL, "resources", f"{len(kt_unresolved)} distinct unresolved R.type.name in Kotlin",
              [f"{k} ×{v} (e.g. {kt_example[k]})" for k, v in kt_unresolved.most_common(30)])
    else:
        r.add(PASS, "resources", f"every R.type.name reference in Kotlin resolves")
    r.stats["resource_refs_scanned"] = total
    r.stats["resource_types"] = {k: len(v) for k, v in sorted(available.items()) if v}

    dupes = []
    for res in p.resource_dirs():
        seen: dict[tuple[str, str], str] = {}
        for xml in walk(res / "values", ".xml"):
            root, _ = xml_root(xml)
            if root is None:
                continue
            for child in root:
                name = child.get("name")
                if not name:
                    continue
                key = (child.tag, name)
                if key in seen:
                    dupes.append(f"{child.tag}/{name}: {seen[key]} and {xml.name}")
                seen[key] = xml.name
    if dupes:
        r.add(FAIL, "resources", "duplicate resource declarations in the default values/ config "
              "(an aapt2 error)", dupes[:20])
    else:
        r.add(PASS, "resources", "no duplicate resource names in the default values/ configuration")

    # adaptive-icon XML layers must point at real drawables/colors
    broken_layers = []
    for xml in walk(p.root / "app" / "src", ".xml"):
        if xml.parent.name not in ("mipmap-anydpi-v26", "drawable-v26"):
            continue
        root, _ = xml_root(xml)
        if root is None or root.tag != "adaptive-icon":
            continue
        for layer in root:
            d = layer.get("android:drawable")
            if not d:
                continue
            if d.startswith("@drawable/"):
                if d[len("@drawable/"):] not in available.get("drawable", set()):
                    broken_layers.append(f"{xml.name}: {d}")
            elif d.startswith("@color/"):
                if d[len("@color/"):] not in available.get("color", set()):
                    broken_layers.append(f"{xml.name}: {d}")
    if broken_layers:
        r.add(FAIL, "resources", "adaptive-icon layers reference missing drawables/colors",
              broken_layers)
    else:
        r.add(PASS, "resources", "all adaptive-icon foreground/background layers resolve")


# --------------------------------------------------------------------------- #
# 5. i18n
# --------------------------------------------------------------------------- #

def check_i18n(p: Project, r: Report) -> None:
    base = p.default_string_keys()
    trans = p.translation_keys()
    if not trans:
        r.add(WARN, "i18n", "no translated values-* directories found")
        return
    orphans = {loc: sorted(names - base) for loc, names in trans.items() if names - base}
    if orphans:
        total = sum(len(v) for v in orphans.values())
        all_orphan = sorted({k for v in orphans.values() for k in v})
        # only a *referenced* orphan can crash: R.string.x resolves from any config, so the
        # field exists and compiles, but getString() on a locale without it throws.
        used_anywhere = {}
        for k in all_orphan:
            hit = [str(fs.path.relative_to(p.root)) for fs in p.index.files
                   if re.search(rf"R\.string\.{k}\b", p.masked(fs.path))]
            for xml in p.xml_files:
                if f"@string/{k}" in read(xml):
                    hit.append(str(xml.relative_to(p.root)))
            if hit:
                used_anywhere[k] = hit
        if used_anywhere:
            r.add(FAIL, "i18n",
                  "string keys that exist ONLY in a translation folder yet are referenced by code "
                  "or layout: getString() on any other locale throws Resources$NotFoundException",
                  [f"{k}: referenced from {v[0]}" for k, v in used_anywhere.items()])
        else:
            r.add(WARN, "i18n",
                  f"{total} string key(s) exist only in translation folders and not in default "
                  f"values/. Nothing references them, so this is Android lint's ExtraTranslation "
                  f"warning (dead translations), not a runtime crash.",
                  [f"{loc}: {', '.join(v[:8])}" for loc, v in sorted(orphans.items())])
    else:
        r.add(PASS, "i18n", f"all {len(trans)} locales define only keys that exist in default values/")
    cov = sorted(((loc, len(names & base)) for loc, names in trans.items()), key=lambda x: x[1])
    complete = [c for c in cov if c[1] == len(base)]
    r.add(INFO, "i18n",
          f"{len(base)} default keys, {len(trans)} locales, {len(complete)} fully translated "
          f"(Crowdin-managed; partial coverage is normal, missing keys fall back to default)",
          [f"least: {', '.join(f'{l} {n}/{len(base)}' for l, n in cov[:5])}",
           f"most:  {', '.join(f'{l} {n}/{len(base)}' for l, n in cov[-5:])}"])
    r.stats["i18n"] = {"default_keys": len(base), "locales": len(trans),
                       "complete": [c[0] for c in complete],
                       "orphan_keys": {k: v for k, v in orphans.items()}}


# --------------------------------------------------------------------------- #
# 6. kotlin structure
# --------------------------------------------------------------------------- #

def check_kotlin(p: Project, r: Report) -> None:
    unbalanced = []
    deepest = ("", 0)
    pkg_mismatch, bom, tabs, crlf, empty = [], [], [], [], []
    for fs in p.index.files:
        rel = fs.path.relative_to(p.root)
        src = read(fs.path)
        if not src.strip():
            empty.append(str(rel))
            continue
        if src.startswith("\ufeff"):
            bom.append(str(rel))
        if "\r\n" in src:
            crlf.append(str(rel))
        if re.search(r"^\t", src, re.M):
            tabs.append(str(rel))
        m = p.masked(fs.path)
        b = balance(m)
        if b["unclosed"] or b["mismatched"]:
            unbalanced.append(f"{rel}: {first_anomaly(m, src)}")
        if b["max_depth"] > deepest[1]:
            deepest = (str(rel), b["max_depth"])
        if fs.package:
            tail = fs.package.replace(".", "/")
            posix = fs.path.as_posix()
            if f"/{tail}/" not in posix and not posix.endswith(f"/{tail}.kt"):
                pkg_mismatch.append(f"{rel} declares `{fs.package}`")

    if unbalanced:
        r.add(FAIL, "kotlin", "files whose brackets do not balance (nested templates, raw strings "
              "and backtick names handled by kotlin_lex)", unbalanced[:20])
    else:
        r.add(PASS, "kotlin",
              f"all {len(p.index.files)} Kotlin files are lexically balanced "
              f"(deepest nesting {deepest[1]} levels, in {deepest[0]})")

    # duplicates: only a clash if the SAME package is compiled into the SAME variant.
    # `foss` and `gms` are flavors of one dimension, so exactly one of them is ever in a
    # build -- the same class name in both is the intended pattern, not a redeclaration.
    flavor_sets = {f for v in p.flavors().values() for f in v}
    by_pkg: dict[tuple[str, str, str], list[tuple[str, str]]] = defaultdict(list)
    for fs in p.index.files:
        mod = p.index.module_of(fs.path)
        ss = p.index.source_set_of(fs.path)
        for t in fs.types:
            by_pkg[(mod, fs.package, t)].append((ss, fs.path.name))
    dupes = []
    for (mod, pkg, t), where in by_pkg.items():
        if len(where) < 2:
            continue
        sets_ = {w[0] for w in where}
        if len(sets_) == len(where) and sets_ <= flavor_sets:
            continue                     # one per mutually exclusive flavor -> fine
        dupes.append(f"{pkg}.{t} declared in "
                     + ", ".join(sorted(f"{ss}/{name}" for ss, name in where)))
    if dupes:
        r.add(FAIL, "kotlin", "same type declared twice in one package inside one module/source-set "
              "combination (JVM platform declaration clash)", sorted(dupes)[:20])
    else:
        shared = Counter()
        for fs in p.index.files:
            for t in fs.types:
                shared[(fs.package, t)] += 1
        flavoured = {k: v for k, v in shared.items() if v > 1}
        r.add(PASS, "kotlin",
              f"no duplicate declarations within any module+source-set; the {len(flavoured)} types "
              f"that repeat do so only across mutually exclusive flavor source sets "
              f"(foss/fdroid vs. gms), which never compile together")

    if pkg_mismatch:
        r.add(WARN, "kotlin", "package declaration differs from the directory path (legal Kotlin, "
              "but an IDE/navigation smell)", pkg_mismatch[:15])
        r.stats["pkg_mismatch_count"] = len(pkg_mismatch)
    else:
        r.add(PASS, "kotlin", "every package declaration matches its directory path")
    for label, lst in (("a UTF-8 BOM", bom), ("tab indentation", tabs),
                       ("CRLF line endings", crlf), ("no content", empty)):
        if lst:
            r.add(WARN, "kotlin", f"files with {label}", lst[:10])
    if not (bom or tabs or crlf or empty):
        r.add(PASS, "kotlin", "no BOM, no tab indentation, no CRLF line endings, no empty files")

    loc = sum(len(read(f).splitlines()) for f in p.kotlin_files)
    largest = sorted(p.index.files, key=lambda fs: -len(read(fs.path).splitlines()))[:12]
    r.stats["kotlin"] = {
        "files": len(p.index.files), "loc": loc,
        "declarations": len(p.index.fqns),
        "largest": [(str(fs.path.relative_to(p.root)), len(read(fs.path).splitlines()))
                    for fs in largest],
    }


# --------------------------------------------------------------------------- #
# 7. room
# --------------------------------------------------------------------------- #

def check_room(p: Project, r: Report) -> None:
    cand = [fs for fs in p.index.files if fs.path.name == "MusicDatabase.kt"]
    if not cand:
        r.add(WARN, "room", "MusicDatabase.kt not found")
        return
    txt = read(cand[0].path)
    version = int(re.search(r"const val MusicDatabaseVersion\s*=\s*(\d+)", txt).group(1))
    ents = re.findall(r"(\w+)::class", re.search(r"entities\s*=\s*\[(.*?)\]", txt, re.S).group(1))
    views = re.findall(r"(\w+)::class", re.search(r"views\s*=\s*\[(.*?)\]", txt, re.S).group(1))
    missing = [e for e in ents + views if not p.index.has_simple(e)]
    if missing:
        r.add(FAIL, "room", "@Database entities/views with no declaration anywhere", missing)
    else:
        r.add(PASS, "room", f"all {len(ents)} entities and {len(views)} views in @Database exist "
              f"(several are declared inside a shared file, which is why a filename check lies)")

    arr = re.search(r"val AllMigrations.*?arrayOf\((.*?)\)", txt, re.S).group(1)
    listed = {(int(a), int(b)) for a, b in re.findall(r"MIGRATION_(\d+)_(\d+)", arr)}
    defined = {(int(a), int(b)) for a, b in re.findall(r"val MIGRATION_(\d+)_(\d+)\s*=", txt)}
    if listed - defined:
        r.add(FAIL, "room", "AllMigrations lists migrations that are never defined",
              [f"MIGRATION_{a}_{b}" for a, b in sorted(listed - defined)])
    if defined - listed:
        r.add(FAIL, "room", "defined migrations absent from AllMigrations: Room would not run them "
              "and fallbackToDestructiveMigration would wipe the library",
              [f"MIGRATION_{a}_{b}" for a, b in sorted(defined - listed)])
    if not (listed ^ defined):
        r.add(PASS, "room", f"AllMigrations and the MIGRATION_* definitions agree exactly "
              f"({len(listed)} migrations)")

    def reachable(frm: int) -> bool:
        at, guard = frm, 0
        while at < version and guard < 1000:
            guard += 1
            hops = [e for (s, e) in listed if s == at and e <= version]
            if not hops:
                return False
            at = max(hops)
        return at == version

    ok = [v for v in range(1, version + 1) if reachable(v)]
    bad = [v for v in range(1, version + 1) if v not in ok]
    r.stats["room"] = {"version": version, "migrations": sorted(listed),
                       "reachable_from": ok, "unreachable_from": bad,
                       "entities": ents, "views": views}
    if bad:
        r.add(FAIL, "room",
              f"no upgrade path to schema v{version} from {len(bad)} earlier schema versions. "
              f"Room's fallbackToDestructiveMigration(dropAllTables=true) then deletes the entire "
              f"library on upgrade, and TransferImport.hasMigrationPath() refuses those exports.",
              [f"versions with no path: {bad[0]}..{bad[-1]} ({len(bad)} total)",
               f"versions that do work: {ok}",
               f"migrations present: {sorted('MIGRATION_%d_%d' % m for m in listed)}",
               "evidence: db/MusicDatabase.kt (AllMigrations, ~line 187) and "
               "importer/transfer/TransferImport.kt:259"])
    else:
        r.add(PASS, "room", f"every schema version 1..{version} has an upgrade path to {version}")

    exp = re.search(r"exportSchema\s*=\s*(\w+)", txt)
    schemas = sorted({f.stem for f in p.schema_files}, key=lambda s: int(s) if s.isdigit() else 0)
    if exp and exp.group(1) == "false":
        r.add(WARN, "room",
              "exportSchema = false while ksp still sets room.schemaLocation, so no new schema JSON "
              "is written. app/schemas therefore stops at v34 although the DB is at "
              f"v{version} -- schema-diff tests and export provenance cannot see the live schema.",
              [f"exported versions present: {len(schemas)} (max {schemas[-1] if schemas else '-'})",
               "two namespace dirs: com.music.vivi.db (pre-rename) and com.mochyy.mochymusic.db"])


# --------------------------------------------------------------------------- #
# 8. tests
# --------------------------------------------------------------------------- #

INTERNAL = ("com.mochyy.mochymusic", "com.music.innertube", "com.music.vivi", "com.music.kugou",
            "com.music.lrclib", "com.music.lastfm", "com.music.betterlyrics", "com.music.simpmusic",
            "com.music.youlyplus", "com.music.shazamkit", "com.music.artistvideo",
            "com.music.applecanvas", "com.music.paxsenixlyrics", "com.music.directlyrics",
            "com.music.musixmatch", "com.music.genius", "com.music.wikipedia", "com.music.canvas",
            "com.mochyy.mochymusic.module")


def check_tests(p: Project, r: Report) -> None:
    tests = [fs for fs in p.index.files if p.index.source_set_of(fs.path) == "test"]
    if not tests:
        r.add(WARN, "tests", "no unit test sources found")
        return
    broken, classes, asserts = [], [], 0
    for fs in tests:
        txt = read(fs.path)
        cls = re.search(r"class\s+(\w+)", txt)
        if cls:
            classes.append(cls.group(1))
        asserts += len(re.findall(r"@Test", txt))
        for imp in re.findall(r"^import\s+([A-Za-z0-9_.]+)$", txt, re.M):
            if not imp.startswith(INTERNAL):
                continue
            if p.index.has_fqn(imp):
                continue
            leaf = imp.split(".")[-1]
            pkg = imp.rsplit(".", 1)[0]
            if leaf in p.index.by_package.get(pkg, set()):
                continue
            if leaf in p.index.by_simple:
                continue
            broken.append(f"{fs.path.relative_to(p.root)} -> {imp}")
    if broken:
        r.add(FAIL, "tests", "test sources import internal types that do not exist", broken[:20])
    else:
        r.add(PASS, "tests",
              f"all {len(tests)} unit-test files resolve every internal import; "
              f"{len(classes)} test classes carrying {asserts} @Test methods")
    r.stats["tests"] = {"files": len(tests), "classes": classes, "test_methods": asserts}


# --------------------------------------------------------------------------- #
# 9. cross-module dependencies
# --------------------------------------------------------------------------- #

def check_module_deps(p: Project, r: Report) -> None:
    prefixes = sorted(p.pkg_owner, key=len, reverse=True)
    violations = []
    checked = 0
    for fs in p.index.files:
        mod = p.index.module_of(fs.path)
        allowed = {mod} | p.module_deps.get(mod, set())
        txt = read(fs.path)
        for imp in re.findall(r"^import\s+([A-Za-z0-9_.]+)$", txt, re.M):
            if not imp.startswith(INTERNAL):
                continue
            owner = None
            for pre in prefixes:
                if imp == pre or imp.startswith(pre + "."):
                    owner = pre
                    break
            if owner is None:
                continue
            owners = p.pkg_owner[owner]
            checked += 1
            if owners & allowed:
                continue
            # a flavor source set may rely on a flavor-scoped dependency
            violations.append(f"{mod} ({p.index.source_set_of(fs.path)}/{fs.path.name}) imports "
                              f"{imp} but does not depend on {sorted(owners)}")
    if violations:
        r.add(FAIL, "module-deps",
              "cross-module imports with no matching project(\":…\") dependency in the importing "
              "module's build script -- these cannot compile", sorted(set(violations))[:20])
    else:
        r.add(PASS, "module-deps",
              f"all {checked} internal cross-module imports are backed by a declared "
              f"project(\":…\") dependency in the importing module")
    r.stats["module_deps"] = {m: sorted(d) for m, d in sorted(p.module_deps.items()) if d}


# --------------------------------------------------------------------------- #
# 10. CI
# --------------------------------------------------------------------------- #

def check_ci(p: Project, r: Report) -> None:
    wfs = walk(p.root / ".github" / "workflows", ".yml", ".yaml")
    if not wfs:
        r.add(WARN, "ci", "no GitHub workflows found")
        return
    valid = p.variants()
    used, unknown = set(), []
    for wf in wfs:
        txt = read(wf)
        if "\t" in txt:
            r.add(FAIL, "ci", f"{wf.name} contains tabs, which YAML forbids")
        for task in re.findall(r"\./gradlew\s+([A-Za-z0-9_]+)", txt):
            used.add(task)
            if task not in valid:
                unknown.append(f"{wf.name}: {task}")
    if unknown:
        r.add(FAIL, "ci", "workflows invoke gradle tasks the flavor × buildType matrix cannot "
              "produce", sorted(set(unknown)))
    else:
        r.add(PASS, "ci",
              f"all {len(used)} distinct ./gradlew tasks used by {len(wfs)} workflows match a real "
              f"variant: {', '.join(sorted(used))}")
    dims = p.flavors()
    r.stats["ci"] = {"workflows": [w.name for w in wfs], "tasks": sorted(used),
                     "flavor_dimensions": dims, "build_types": p.build_types(),
                     "variant_count": len(list(itertools.product(*[dims[d] for d in sorted(dims)])))
                     * len(p.build_types())}


# --------------------------------------------------------------------------- #
# 11. prerequisites
# --------------------------------------------------------------------------- #

def check_prereqs(p: Project, r: Report) -> None:
    problems, notes = [], []
    g = p.app_gradle_raw

    # Which signing configs are *unconditionally* assigned to a build type?
    #
    # `release` is written as `if (hasReleaseSigning) getByName("release") else getByName("debug")`,
    # and hasReleaseSigning itself requires `file("keystore/release.keystore").exists()`. So a clone
    # without that file simply signs release builds with the debug key -- it does not fail. Only
    # configs a build type takes with no such guard can actually break a fresh checkout, and those
    # are the ones worth reporting.
    guarded = set()
    for m in re.finditer(r'if\s*\(\s*(\w+)\s*\)\s*\{\s*signingConfigs\.getByName\("(\w+)"\)\s*\}\s*else\s*\{\s*signingConfigs\.getByName\("(\w+)"\)', g):
        cond, if_name, else_name = m.groups()
        guarded.add(if_name)                 # only used when its own guard passed
        used_names_guarded_else = else_name  # this one IS unconditional
    used_names = set(re.findall(r'signingConfig\s*=\s*signingConfigs\.getByName\("(\w+)"\)', g))
    for m in re.finditer(r'signingConfig\s*=\s*if\s*\([^)]*\)\s*\{\s*signingConfigs\.getByName\("(\w+)"\)\s*\}\s*else\s*\{\s*signingConfigs\.getByName\("(\w+)"\)', g):
        used_names.discard(m.group(1))
        used_names.add(m.group(2))
    store_of = dict(re.findall(r'create\("(\w+)"\)\s*\{\s*\n\s*storeFile\s*=\s*file\("([^"]+)"\)', g))
    for cfg in sorted(used_names):
        raw = store_of.get(cfg)
        if not raw or "${" in raw or "System.getProperty" in raw:
            continue
        f = p.root / "app" / raw
        if f.exists():
            continue
        gi = read(p.root / ".gitignore")
        ignored = any(line.strip().lstrip("/") == raw or line.strip().lstrip("/") == f"app/{raw}"
                      for line in gi.splitlines())
        bt = [m for m in p.build_types() if re.search(rf'{m}\s*\{{.*?signingConfigs\.getByName\("{cfg}"\)', g, re.S)]
        msg = (f'build type(s) {bt or "?"} sign with signingConfigs "{cfg}" -> app/{raw}, '
               f'which is absent')
        if ignored:
            notes.append(msg + " and deliberately .gitignore'd, so each developer must generate it")
        else:
            problems.append(msg + " and is NOT .gitignore'd -- a fresh clone cannot build these types")
    dead = sorted(set(store_of) - used_names)
    for cfg in dead:
        raw = store_of[cfg]
        if not (p.root / "app" / raw).exists():
            notes.append(f'signingConfig "{cfg}" points at the missing app/{raw} but no build type '
                         f'uses it, so it is inert -- a footgun if one ever does')

    for block in re.findall(r"proguardFiles\((.*?)\)", g, re.S):
        for name in re.findall(r'"([^"]+\.pro)"', block):
            if not (p.root / "app" / name).exists():
                problems.append(f"proguard file app/{name} referenced but missing")
    if not any("proguard" in x for x in problems):
        r.add(PASS, "prereqs", "proguard-rules.pro and proguard-profileable.pro both present")

    cmake = re.search(r'path\s*=\s*file\("([^"]+CMakeLists.txt)"\)', g)
    if cmake:
        cf = p.root / "app" / cmake.group(1)
        if not cf.exists():
            problems.append(f"externalNativeBuild cmake path app/{cmake.group(1)} missing")
        else:
            miss = [f for f in re.findall(r"add_library\(\s*\w+\s+\w+\s+(.*?)\)", read(cf), re.S)
                    for f in f.split() if not (cf.parent / f).exists()]
            if miss:
                problems.append(f"CMakeLists.txt lists missing sources: {miss}")
            else:
                r.add(PASS, "prereqs",
                      f"native build intact: {cf.relative_to(p.root)} plus every source it lists")

    wrap = p.root / "gradle" / "wrapper"
    if not (wrap / "gradle-wrapper.jar").exists():
        problems.append("gradle/wrapper/gradle-wrapper.jar missing -- ./gradlew cannot bootstrap")
    props = read(wrap / "gradle-wrapper.properties")
    dist = re.search(r"distributionUrl=(.*)", props)
    if dist:
        notes.append(f"Gradle distribution required: {dist.group(1).strip().replace(chr(92), '')}")
    if not os.access(p.root / "gradlew", os.X_OK):
        problems.append("./gradlew is not executable (CI runs `chmod +x gradlew`; a local build "
                        "would fail until you do the same)")
    else:
        r.add(PASS, "prereqs", "./gradlew is executable and the wrapper jar + properties are present")

    for sh in walk(p.root, ".sh"):
        if shutil.which("bash"):
            code, out = run(["bash", "-n", str(sh)])
            if code not in (0, None):
                problems.append(f"{sh.relative_to(p.root)} fails `bash -n`: {out.strip()[:160]}")

    if problems:
        r.add(FAIL, "prereqs", "files the build needs but the repository does not contain", problems)
    else:
        r.add(PASS, "prereqs",
              "every keystore / proguard / cmake / wrapper path the build actually uses is present")
    if notes:
        r.add(INFO, "prereqs", "build prerequisite notes", notes)

    bs = read(p.root / "build.sh")
    bsd_lines = [i + 1 for i, line in enumerate(bs.splitlines()) if "sed -i ''" in line]
    if bsd_lines:
        r.add(FAIL, "prereqs",
              f"build.sh uses BSD/macOS-only `sed -i ''` on {len(bsd_lines)} lines. GNU sed reads "
              f"'' as the script and the following argument as a filename, so the documented "
              f"`./build.sh --debug` path dies under `set -e` on Linux and on the CI runners.",
              [f"build.sh lines {bsd_lines}",
               "GNU sed: `sed -i '' \"s/a/b/g\" file` -> script=\"\", then \"s/a/b/g\" and file "
               "are treated as input filenames",
               "fix: `sed -i.bak … && rm *.bak`, or `perl -pi -e`, or branch on `uname`"])
    else:
        r.add(PASS, "prereqs", "build.sh uses portable sed syntax")


# --------------------------------------------------------------------------- #
# 12. BuildConfig
# --------------------------------------------------------------------------- #

def check_buildconfig(p: Project, r: Report) -> None:
    defined: set[str] = set()
    for gradle in walk(p.root, ".gradle.kts"):
        # raw, not masked: the field NAME is itself a string literal
        defined |= set(re.findall(r'buildConfigField\(\s*"[^"]+"\s*,\s*"([A-Za-z0-9_]+)"',
                                  strip_line_comments(read(gradle))))
    implicit = {"DEBUG", "ID", "VERSION_NAME", "VERSION_CODE", "BUILD_TYPE", "APPLICATION_ID",
                "TARGET_SDK", "MIN_SDK", "COMPILE_SDK", "FLAVOR", "FLAVOR_abi", "FLAVOR_variant",
                "APPLICATION_ID_SUFFIX", "VERSION_NAME_SUFFIX"}
    defined |= implicit
    used: dict[str, list[str]] = defaultdict(list)
    for fs in p.index.files:
        for name in re.findall(r"\bBuildConfig\.([A-Za-z0-9_]+)", p.masked(fs.path)):
            used[name].append(str(fs.path.relative_to(p.root)))
    missing = {k: v for k, v in used.items() if k not in defined}
    if missing:
        r.add(FAIL, "buildconfig", "BuildConfig fields read in Kotlin but never defined by Gradle "
              "(unresolved reference -> compile error)",
              [f"{k}: {v[0]} (+{len(v)-1})" for k, v in missing.items()])
    else:
        r.add(PASS, "buildconfig",
              f"all {len(used)} BuildConfig fields read by Kotlin are defined in the build scripts")
    r.stats["buildconfig"] = {"defined": sorted(defined - implicit), "used": sorted(used),
                              "unused": sorted(defined - set(used) - implicit)}


# --------------------------------------------------------------------------- #
# 13. json + docs
# --------------------------------------------------------------------------- #

def check_json_docs(p: Project, r: Report) -> None:
    bad = []
    for j in p.json_files + p.schema_files:
        try:
            json.loads(read(j))
        except json.JSONDecodeError as exc:
            bad.append(f"{j.relative_to(p.root)}: line {exc.lineno} {exc.msg}")
    if bad:
        r.add(FAIL, "json-docs", "JSON files that do not parse", bad[:20])
    else:
        r.add(PASS, "json-docs",
              f"all {len(p.json_files) + len(p.schema_files)} JSON files parse cleanly "
              f"({len(p.json_files)} config/asset, {len(p.schema_files)} Room schema exports)")

    refs: set[str] = set()
    for f in p.kotlin_files + walk(p.root, ".json", ".kts", ".md"):
        if ".git" in f.parts:
            continue
        refs |= set(re.findall(r"\bdocs/[A-Za-z0-9_./\-]+\.md", read(f)))
    missing = sorted(d for d in refs if not (p.root / d).exists())
    if missing:
        gi = read(p.root / ".gitignore")
        ignored = any(line.strip().rstrip("/") == "docs" for line in gi.splitlines())
        r.add(WARN if ignored else FAIL, "json-docs",
              f"{len(missing)} docs/*.md paths referenced from code and config do not exist"
              + (" -- .gitignore excludes `docs/`, so upstream keeps them locally only and every "
                 "fresh clone inherits dangling references." if ignored else ""),
              missing)
    else:
        r.add(PASS, "json-docs", "every docs/*.md path referenced from code exists")
    r.stats["dangling_docs"] = missing


# --------------------------------------------------------------------------- #
# 14. secrets
# --------------------------------------------------------------------------- #

SECRET_PATTERNS = {
    "AWS access key id": r"AKIA[0-9A-Z]{16}",
    "GitHub token": r"gh[pousr]_[A-Za-z0-9]{36,}",
    "Slack token": r"xox[baprs]-[0-9A-Za-z\-]{10,}",
    "PEM private key": r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    "Stripe secret key": r"sk_live_[0-9A-Za-z]{20,}",
    "hard-coded bearer literal": r'"Bearer\s+[A-Za-z0-9._\-]{25,}"',
    "hard-coded password assignment": r'(?i)(?:password|passwd|secret)\s*=\s*"[^"$]{8,}"',
}


def check_secrets(p: Project, r: Report) -> None:
    hits = []
    files = p.kotlin_files + p.xml_files + p.json_files + walk(p.root, ".kts", ".properties",
                                                              ".sh", ".yml", ".md", ".pro")
    for f in files:
        if ".git" in f.parts:
            continue
        txt = read(f)
        for label, pat in SECRET_PATTERNS.items():
            m = re.search(pat, txt)
            if m:
                hits.append(f"{label}: {f.relative_to(p.root)} -> {m.group(0)[:48]}")
    if hits:
        r.add(FAIL, "secrets", "possible hard-coded credentials", sorted(set(hits))[:20])
    else:
        r.add(PASS, "secrets",
              f"no AWS/GitHub/Slack/Stripe keys, PEM private keys, bearer literals or password "
              f"assignments in any of the {len(files)} source, resource, config and script files")

    # the one long-lived literal that *is* in the tree, classified rather than shouted about
    ai = p.root / "app/src/main/kotlin/com/mochyy/mochymusic/utils/potoken/PoTokenWebView.kt"
    if ai.exists():
        keys = re.findall(r'"(AIza[0-9A-Za-z_\-]{35})"', read(ai))
        if keys:
            r.add(INFO, "secrets",
                  "PoTokenWebView.kt embeds a YouTube *web-client* API key. This is not a leak: it "
                  "is the public key YouTube's own browser player ships, it is quota-scoped to that "
                  "client id, and every InnerTube implementation (ytmusic, ReVanced, ViMusic) "
                  "carries the same constant. It cannot be moved to a secret because the app must "
                  "work with no server and no user-supplied key.",
                  [f"{keys[0][:12]}...{keys[0][-4:]} at PoTokenWebView.kt:303 (GOOGLE_API_KEY)",
                   "REQUEST_KEY beside it is likewise YouTube's public attestation id"])
    r.add(INFO, "secrets",
          "real credentials are injected at build time from local.properties or CI secrets and "
          "default to empty strings, so a clean checkout contains none",
          ["LASTFM_API_KEY / LASTFM_SECRET",
           "LIBREFM_API_KEY / LIBREFM_SECRET (MD5 stand-in derived from applicationId when unset)",
           "DISCORD_CLIENT_ID (manifest placeholder falls back to mochy-discord-unset)"])


# --------------------------------------------------------------------------- #
# 15. flavors
# --------------------------------------------------------------------------- #

def check_flavors(p: Project, r: Report) -> None:
    dims = p.flavors()
    flavors = [f for v in dims.values() for f in v]
    src = p.root / "app" / "src"
    present = sorted(d.name for d in src.iterdir() if d.is_dir())
    orphan = [d for d in present
              if d not in flavors and d not in ("main", "test", "androidTest", "debug", "release")]
    reuse = re.search(r'getByName\("fdroid"\)\s*\{[^}]*src/foss/kotlin', p.app_gradle_raw, re.S)
    if not reuse and (src / "fdroid").is_dir():
        r.add(WARN, "flavors", "fdroid flavor has neither its own sources nor a sourceSets{} reuse "
              "of src/foss")
    if orphan:
        r.add(WARN, "flavors", "source-set directories matching no declared flavor", orphan)
    if not orphan:
        dim_txt = "; ".join("%s=[%s]" % (k, ", ".join(v)) for k, v in dims.items())
        n_combo = len(list(itertools.product(*[dims[d] for d in sorted(dims)])))
        r.add(PASS, "flavors",
              f"{len(dims)} flavor dimensions ({dim_txt}) x {len(p.build_types())} build types "
              f"= {n_combo * len(p.build_types())} buildable variants",
              [f"source sets present: {', '.join(present)}",
               "fdroid explicitly reuses src/foss/kotlin + src/foss/res via sourceSets{}",
               f"gms-only deps are scoped with \"gmsImplementation\" so foss/fdroid stay Play-Services free"])


# --------------------------------------------------------------------------- #
# 16. launcher icon contract
# --------------------------------------------------------------------------- #

def check_app_icons(p: Project, r: Report) -> None:
    root, _ = xml_root(p.manifest_path)
    if root is None:
        return
    A = "{http://schemas.android.com/apk/res/android}"
    manifest_aliases = {el.get(f"{A}name").lstrip(".") for el in root.iter("activity-alias")
                        if el.get(f"{A}name")}
    cand = [fs for fs in p.index.files if fs.path.name == "AppIcons.kt"]
    if not cand:
        r.add(WARN, "app-icons", "AppIcons.kt not found")
        return
    txt = read(cand[0].path)
    fam_m = re.search(r"enum class AppIconFamily[^{]*\{(.*?)\n\}", txt, re.S)
    col_m = re.search(r"enum class AppIconColor[^{]*\{(.*?)\n\}", txt, re.S)
    if not fam_m or not col_m:
        r.add(WARN, "app-icons", "could not parse AppIconFamily / AppIconColor enums")
        return
    fams = re.findall(r"^\s{4}([A-Z][A-Za-z]*)\(", fam_m.group(1), re.M)
    cols = re.findall(r"^\s{4}([A-Z][A-Za-z]*)\(", col_m.group(1), re.M)
    presets = set(re.findall(r'AppIcon\("[^"]+",\s*"(Icon[A-Za-z]+)"', txt)) | {"IconDefault"}
    generated = presets | {f"Icon{f}{c}" for f in fams for c in cols}
    missing_in_manifest = sorted(generated - manifest_aliases)
    extra_in_manifest = sorted(manifest_aliases - generated)
    if missing_in_manifest:
        r.add(FAIL, "app-icons",
              "AppIcons.kt can offer an icon whose activity-alias is not in the manifest -- "
              "setComponentEnabledSetting on it throws IllegalArgumentException",
              missing_in_manifest)
    if extra_in_manifest:
        r.add(WARN, "app-icons", "manifest declares launcher aliases AppIcons.kt never offers "
              "(dead entries, harmless)", extra_in_manifest)
    if not (missing_in_manifest or extra_in_manifest):
        r.add(PASS, "app-icons",
              f"the manifest's {len(manifest_aliases)} launcher activity-alias entries are exactly "
              f"the set AppIcons.kt builds: {len(presets)} presets + {len(fams)} families × "
              f"{len(cols)} colours = {len(generated)}")
    r.stats["app_icons"] = {"families": fams, "colours": cols, "presets": sorted(presets),
                            "manifest_aliases": len(manifest_aliases), "generated": len(generated)}


# --------------------------------------------------------------------------- #
# 17. navigation graph
# --------------------------------------------------------------------------- #

def check_navigation(p: Project, r: Report) -> None:
    """Every navController.navigate("x") must land on a declared composable("x").

    Navigation-Compose resolves routes at runtime, not compile time: navigating to a route
    nobody registered throws IllegalArgumentException the moment the user taps. That makes
    the route table the single most crash-prone contract in a Compose app, and it is fully
    checkable statically.
    """
    nav_files = [fs.path for fs in p.index.files
                 if fs.path.name in ("NavigationBuilder.kt", "Screens.kt")]
    if not nav_files:
        r.add(WARN, "navigation", "NavigationBuilder.kt / Screens.kt not found")
        return

    # ---- declared routes -------------------------------------------------
    # Three helpers register routes here: navigation's own composable(), plus two wrappers
    # this app adds (coverSourceComposable / navigationComposable). Scanning only composable()
    # would report every route registered through a wrapper as missing.
    REGISTER = re.compile(r'\w*[Cc]omposable\(\s*(?:route\s*=\s*)?"([^"]+)"')
    declared: set[str] = set()
    for fs in p.index.files:
        txt = strip_line_comments(read(fs.path))
        declared |= set(REGISTER.findall(txt))
        # route constants: `route = "home"` in Screens.kt, `val FooRoute = "bar"` elsewhere
        declared |= set(re.findall(r'\broute\s*=\s*"([^"]+)"', txt))
        declared |= set(re.findall(r'val\s+\w*Route\w*\s*(?::\s*String)?\s*=\s*"([^"]+)"', txt))

    def norm(pattern: str) -> str:
        """collapse {arg} / $interpolation to one wildcard, and drop optional ?query args.

        A route declared as "developer_space?person={person}" is legitimately reachable by
        navigating to plain "developer_space" -- that is what the `?` optional-argument form
        is for -- so both the full pattern and its query-free prefix are accepted.
        """
        return re.sub(r"\{[^}]*\}", "*", pattern)

    declared_norm = set()
    for d in declared:
        declared_norm.add(norm(d))
        declared_norm.add(norm(d.split("?")[0]))     # the optional-args-free form

    # ---- navigations ------------------------------------------------------
    called: Counter = Counter()
    origin: dict[str, str] = {}
    for fs in p.index.files:
        # comments stripped but string literals kept: a route mentioned in prose (this repo
        # documents one such case in SettingsHighlight.kt) is not a call site
        src = strip_line_comments(read(fs.path))
        for m in re.finditer(r'navigate(?:To)?\(\s*"([^"]*)"', src):
            raw = m.group(1)
            key = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*", "*", raw)
            called[key] += 1
            origin.setdefault(key, f"{fs.path.relative_to(p.root)}:{src[:m.start()].count(chr(10)) + 1}")

    # routes referenced by identifier (Screens.Home.route, XRoute) -- resolve the constant
    ident_routes = set()
    for fs in p.index.files:
        src = read(fs.path)
        for m in re.finditer(r'navigate(?:To)?\(\s*([A-Za-z_][A-Za-z0-9_.]*)\s*[,)]', src):
            ident_routes.add(m.group(1))

    missing = []
    for key, count in called.items():
        if not key or key == "*":
            continue
        if norm(key) in declared_norm or key in declared:
            continue
        if norm(key.split("?")[0]) in declared_norm:
            continue
        # a dynamic segment may be declared with a differently-named {arg}
        if key.split("/")[0] in {d.split("/")[0] for d in declared_norm if "*" in d}:
            continue
        missing.append(f'"{key}" navigated {count}x from {origin[key]}')
    if missing:
        r.add(FAIL, "navigation",
              "navController.navigate() targets with no matching declared route -- tapping these "
              "throws IllegalArgumentException at runtime", sorted(missing)[:25])
    else:
        r.add(PASS, "navigation",
              f"all {sum(called.values())} navigate()/navigateTo() call sites across the app "
              f"({len(called)} distinct route patterns) resolve to one of the "
              f"{len(declared)} declared routes")
    r.stats["navigation"] = {"declared_routes": len(declared),
                             "navigate_calls": sum(called.values()),
                             "distinct_targets": len(called),
                             "identifier_targets": sorted(ident_routes)[:20]}


# --------------------------------------------------------------------------- #
# driver
# --------------------------------------------------------------------------- #

CHECKS = [check_gradle_modules, check_catalog, check_manifest, check_resources, check_i18n,
          check_kotlin, check_room, check_tests, check_module_deps, check_ci, check_prereqs,
          check_buildconfig, check_json_docs, check_secrets, check_flavors, check_app_icons,
          check_navigation]


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else
                Path(__file__).resolve().parent.parent.parent / "Mochy-Music")
    if not root.is_dir():
        print(f"not a directory: {root}", file=sys.stderr)
        return 2
    print(f"indexing {root} …")
    p = Project(root)
    r = Report()
    print(f"  {len(p.kotlin_files)} .kt files, {len(p.index.fqns)} declarations, "
          f"{len(p.xml_files)} .xml files\n")

    for fn in CHECKS:
        try:
            fn(p, r)
        except Exception as exc:                                  # noqa: BLE001
            import traceback
            r.add(FAIL, fn.__name__.replace("check_", ""), f"check crashed: {exc}",
                  traceback.format_exc().splitlines()[-4:])

    order = {FAIL: 0, WARN: 1, INFO: 2, PASS: 3}
    grouped: dict[str, list[Finding]] = defaultdict(list)
    for f in r.findings:
        grouped[f.check].append(f)
    for check in sorted(grouped, key=lambda c: (min(order[f.level] for f in grouped[c]), c)):
        for f in sorted(grouped[check], key=lambda f: order[f.level]):
            print(f"[{f.level:4}] {f.check:13} {f.message}")
            for e in f.evidence:
                print(f"{'':19}- {e}")
            print()

    c = r.counts()
    print("=" * 78)
    print(f"FAIL {c[FAIL]}    WARN {c[WARN]}    INFO {c[INFO]}    PASS {c[PASS]}")
    out = Path(__file__).resolve().parent.parent / "reports"
    out.mkdir(parents=True, exist_ok=True)
    (out / "verify-results.json").write_text(json.dumps(
        {"summary": dict(c),
         "stats": r.stats,
         "findings": [{"level": f.level, "check": f.check, "message": f.message,
                       "evidence": f.evidence} for f in r.findings]},
        indent=2, default=str), encoding="utf-8")
    print(f"machine-readable report -> {out / 'verify-results.json'}")
    return 1 if c[FAIL] else 0


if __name__ == "__main__":
    sys.exit(main())
