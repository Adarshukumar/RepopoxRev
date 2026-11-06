"""
kotlin_index.py -- build a symbol index of a Kotlin source tree.

verify.py needs to answer questions a filename cannot: "does the type
`com.mochyy.mochymusic.eq.audio.ReverbParams` exist?" Kotlin declares any number of
top-level and nested types per file, and file names need not match, so the only honest
answer comes from actually reading the declarations.

The index records, for every .kt file:
  * its `package`
  * every top-level and nested `class / interface / object / enum class / annotation class /
    data class / value class / sealed ...` name
  * every top-level `fun` and `val/var` name (imports can target those too)
  * every `typealias`

From that it derives:
  * fqns          -- "com.x.Y"  for every type
  * by_package    -- package -> {simple names}
  * by_simple     -- simple name -> [fqns]     (for fast "does this exist anywhere" checks)
"""

from __future__ import annotations

import os
import re
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

from kotlin_lex import mask_kotlin

DECL_RE = re.compile(
    r"""^\s*(?:(?:public|private|internal|protected|abstract|final|open|sealed|data|value|
                   inner|annotation|expect|actual|external|companion|enum|fun|inline|
                   operator|infix|const|lateinit|suspend|tailrec|vararg)\s+)*
        (?P<kind>class|interface|object|typealias)
        \s+(?P<name>`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)""",
    re.M | re.X,
)
FUN_RE = re.compile(r"^\s*(?:(?:public|private|internal|protected|open|override|final|abstract|"
                    r"inline|operator|infix|external|suspend|tailrec|expect|actual|vararg)\s+)*"
                    r"fun\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][A-Za-z0-9_]*\s*\.\s*)?"
                    r"(?P<name>`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)", re.M)
PROP_RE = re.compile(r"^\s*(?:(?:public|private|internal|protected|open|override|final|const|"
                     r"lateinit|expect|actual|val|var)\s+)+(?:[A-Za-z_][A-Za-z0-9_]*\s*\.\s*)?"
                     r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)", re.M)
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)", re.M)


def declarations(masked: str) -> tuple[set[str], set[str]]:
    """Split declarations into top-level and nested, using brace depth.

    Depth is measured over the *masked* source, so only real code braces count. A type at
    depth 0 is top-level and gets the file's package as its qualifier; anything deeper is
    nested inside an enclosing type, and Kotlin scopes it there -- `private data class
    CacheEntry` inside three different providers is three distinct types, not a clash.
    Reporting those as duplicates would be a lie, so they are tracked separately.
    """
    top: set[str] = set()
    nested: set[str] = set()
    depth = 0
    # stack of (depth_at_open, name) for enclosing types
    scope: list[tuple[int, str]] = []
    i = 0
    lines = masked.split("\n")
    offset = 0
    for line in lines:
        m = DECL_RE.match(line)
        # depth as of the START of this line
        line_depth = depth
        if m:
            nm = m.group("name").strip("`")
            if line_depth == 0:
                top.add(nm)
                scope = [(0, nm)]
            else:
                while scope and scope[-1][0] >= line_depth:
                    scope.pop()
                owner = scope[-1][1] if scope else ""
                nested.add(f"{owner}.{nm}" if owner else nm)
                scope.append((line_depth, nm))
        for ch in line:
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                while scope and scope[-1][0] >= depth:
                    scope.pop()
        offset += len(line) + 1
    return top, nested


@dataclass
class FileSyms:
    path: Path
    package: str
    types: set[str] = field(default_factory=set)          # top-level type names only
    nested: set[str] = field(default_factory=set)         # qualified Outer.Inner names
    funcs: set[str] = field(default_factory=set)
    props: set[str] = field(default_factory=set)


class KotlinIndex:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.files: list[FileSyms] = []
        self.fqns: set[str] = set()
        self.by_package: dict[str, set[str]] = defaultdict(set)
        self.by_simple: dict[str, set[str]] = defaultdict(set)
        self.type_to_file: dict[str, Path] = {}
        self.build()

    def build(self) -> None:
        for dirpath, dirnames, filenames in os.walk(self.root):
            dirnames[:] = [d for d in dirnames if d not in {".git", "build", ".gradle"}]
            for name in sorted(filenames):
                if not name.endswith(".kt"):
                    continue
                path = Path(dirpath) / name
                try:
                    src = path.read_text(encoding="utf-8", errors="replace")
                except OSError:
                    continue
                masked = mask_kotlin(src)
                pm = PACKAGE_RE.search(src)
                pkg = pm.group(1) if pm else ""
                fs = FileSyms(path=path, package=pkg)
                top, nested = declarations(masked)
                fs.types |= top
                fs.nested |= nested
                for m in FUN_RE.finditer(masked):
                    fs.funcs.add(m.group("name").strip("`"))
                for m in PROP_RE.finditer(masked):
                    fs.props.add(m.group("name"))
                self.files.append(fs)
                for t in fs.types:
                    fqn = f"{pkg}.{t}" if pkg else t
                    self.fqns.add(fqn)
                    self.by_simple[t].add(fqn)
                    self.type_to_file.setdefault(t, path)
                    if pkg:
                        self.by_package[pkg].add(t)
                # nested types stay resolvable by simple name (that is how code refers to
                # them) but never get a package-level fqn, so they cannot fake a clash
                for t in fs.nested:
                    self.by_simple[t.split(".")[-1]].add(f"{pkg}.{t}" if pkg else t)
                # top-level functions/properties are importable by their bare fqn
                for f in fs.funcs | fs.props:
                    if pkg:
                        self.by_package[pkg].add(f)
                        self.fqns.add(f"{pkg}.{f}")
                        self.by_simple[f].add(f"{pkg}.{f}")

    # ---- queries -------------------------------------------------------- #
    def has_fqn(self, fqn: str) -> bool:
        return fqn in self.fqns

    def has_simple(self, name: str) -> bool:
        return name in self.by_simple

    def module_of(self, path: Path) -> str:
        """`:app` / `:providers:innertube` style Gradle module for a source path."""
        rel = path.relative_to(self.root).as_posix()
        parts = rel.split("/")
        if parts[0] == "app":
            return ":app"
        if parts[0] in ("modules", "providers") and len(parts) > 1:
            return f":{parts[0]}:{parts[1]}"
        return ":" + parts[0]

    def source_set_of(self, path: Path) -> str:
        """`main`, `test`, `foss`, `gms`, `fdroid`, `debug`, ... for an app/module path."""
        rel = path.relative_to(self.root).as_posix().split("/")
        if "src" in rel:
            i = rel.index("src")
            if i + 1 < len(rel):
                return rel[i + 1]
        return "?"

    def files_by_stem(self) -> dict[str, list[Path]]:
        out: dict[str, list[Path]] = defaultdict(list)
        for fs in self.files:
            out[fs.path.stem].append(fs.path)
        return dict(out)
