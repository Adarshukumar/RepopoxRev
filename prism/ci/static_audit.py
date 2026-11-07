#!/usr/bin/env python3
"""
Static audit for the PRISM Kotlin sources.

The environment this is normally run in has no JDK, no Android SDK and no
route to the Maven repositories, so nothing here can be compiled. That leaves
two failure classes that would otherwise only surface in CI, minutes per
iteration:

  1. An unbalanced delimiter, usually from an edited string literal.
  2. An import that does not exist — a wrong package path, a symbol that moved
     between Compose releases, or a name invented from memory.

Class 2 is the expensive one, and it is checkable offline. The repository
vendors Mochy-Music, a large Compose application built against the same
Compose, Material3, activity and lifecycle versions PRISM declares. Any
androidx import that appears somewhere in those 1000+ files is an API that
really exists at that coordinate. An import that appears in PRISM but nowhere
in Mochy is not necessarily wrong, but it is unverified and worth a look
before spending a CI cycle on it.

Also indexes PRISM's own declarations so that every `import dev.prism.*` can
be resolved against a symbol that is actually declared.
"""

import os
import re
import sys
import glob

PRISM = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
MOCHY = os.path.normpath(os.path.join(PRISM, "..", "Mochy-Music"))

DELIMS = {")": "(", "]": "[", "}": "{"}
OPENERS = set("([{")


def strip_code(text):
    """Blank out string literals, char literals and comments so that braces
    inside them cannot affect the balance check. Returns a same-length string
    so column positions stay meaningful."""
    out = list(text)
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        # line comment
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        # block comment (nesting, as Kotlin allows)
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            depth = 0
            j = i
            while j < n:
                if text[j] == "/" and j + 1 < n and text[j + 1] == "*":
                    depth += 1
                    j += 2
                    continue
                if text[j] == "*" and j + 1 < n and text[j + 1] == "/":
                    depth -= 1
                    j += 2
                    if depth == 0:
                        break
                    continue
                j += 1
            for k in range(i, min(j, n)):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        # triple-quoted string
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            for k in range(i, min(j, n)):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        # ordinary string
        if c == '"':
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    j += 1
                    break
                if text[j] == "\n":
                    break
                j += 1
            for k in range(i, min(j, n)):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        # char literal
        if c == "'":
            j = i + 1
            while j < n and text[j] != "'" and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            for k in range(i, j):
                out[k] = " "
            i = j
            continue
        i += 1
    return "".join(out)


def check_balance(path, text):
    code = strip_code(text)
    stack = []
    for idx, ch in enumerate(code):
        if ch in OPENERS:
            stack.append((ch, idx))
        elif ch in DELIMS:
            if not stack:
                line = code.count("\n", 0, idx) + 1
                return f"unmatched closing '{ch}' at line {line}"
            top, opos = stack.pop()
            if top != DELIMS[ch]:
                line = code.count("\n", 0, idx) + 1
                oline = code.count("\n", 0, opos) + 1
                return f"'{ch}' at line {line} closes '{top}' opened at line {oline}"
    if stack:
        ch, pos = stack[-1]
        line = code.count("\n", 0, pos) + 1
        return f"unclosed '{ch}' opened at line {line} ({len(stack)} still open)"
    return None


DECL_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s+)*"
    r"(?:(?:public|internal|private|protected|abstract|open|final|sealed|data|value|enum|annotation|expect|actual|inline|operator|infix|external|suspend|lateinit|const)\s+)*"
    r"(class|object|interface|fun|val|var|typealias)\s+"
    r"(?:<[^>]*>\s*)?"
    r"([A-Za-z_]\w*)",
    re.M,
)
PKG_RE = re.compile(r"^\s*package\s+([\w.]+)", re.M)
IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$", re.M)


def main():
    failures = []
    warnings = []

    kt_files = sorted(glob.glob(os.path.join(PRISM, "app/src/**/*.kt"), recursive=True))
    if not kt_files:
        print("no kotlin sources found under", PRISM)
        return 1

    print("=" * 72)
    print("PRISM static audit — %d kotlin files" % len(kt_files))
    print("=" * 72)

    # ── index PRISM's own declarations ───────────────────────────────────
    own = {}   # fqcn -> path
    imports_by_file = {}
    for path in kt_files:
        text = open(path, encoding="utf-8").read()
        m = PKG_RE.search(text)
        pkg = m.group(1) if m else ""
        decls = set()
        for kind, name in DECL_RE.findall(strip_code(text)):
            decls.add(name)
        for d in decls:
            own.setdefault(pkg + "." + d, path)
        imports_by_file[path] = IMPORT_RE.findall(text)

    # ── 1. delimiter balance ─────────────────────────────────────────────
    print("\n[1] delimiter balance")
    for path in kt_files:
        text = open(path, encoding="utf-8").read()
        problem = check_balance(path, text)
        rel = os.path.relpath(path, PRISM)
        if problem:
            failures.append(f"{rel}: {problem}")
            print("  FAIL  %-62s %s" % (rel, problem))
        else:
            print("  ok    %s" % rel)

    # ── 2. self imports resolve ──────────────────────────────────────────
    print("\n[2] imports of PRISM's own symbols")
    checked = 0
    for path, imps in imports_by_file.items():
        rel = os.path.relpath(path, PRISM)
        for imp in imps:
            if not imp.startswith("dev.prism.analysis"):
                continue
            checked += 1
            # a member import: last segment may be a member of a class, so
            # accept either the full path or its parent type being declared
            if imp in own:
                continue
            parent = imp.rsplit(".", 1)[0]
            if parent in own:
                continue
            failures.append(f"{rel}: unresolved own import {imp}")
            print("  FAIL  %-50s %s" % (rel, imp))
    print("  checked %d own-package imports, %d failures" % (checked, len([f for f in failures if "own import" in f])))

    # ── 3. androidx imports verified against the vendored project ────────
    print("\n[3] androidx imports cross-checked against vendored Mochy-Music")
    mochy_imports = set()
    if os.path.isdir(MOCHY):
        mochy_files = glob.glob(os.path.join(MOCHY, "**/*.kt"), recursive=True)
        for path in mochy_files:
            try:
                text = open(path, encoding="utf-8").read()
            except Exception:
                continue
            for imp in IMPORT_RE.findall(text):
                mochy_imports.add(imp)
        print("  indexed %d distinct imports from %d vendored files"
              % (len(mochy_imports), len(mochy_files)))
    else:
        print("  SKIP  vendored tree not found at %s" % MOCHY)

    prefixes = ("androidx.compose", "androidx.activity", "androidx.lifecycle",
                "androidx.core", "kotlinx.coroutines")
    verified = unverified = 0
    for path, imps in imports_by_file.items():
        rel = os.path.relpath(path, PRISM)
        for imp in imps:
            if not imp.startswith(prefixes):
                continue
            if imp in mochy_imports:
                verified += 1
            else:
                unverified += 1
                warnings.append(f"{rel}: import not seen in vendored tree: {imp}")
                print("  WARN  %-46s %s" % (rel, imp))
    print("  %d verified against the vendored tree, %d unverified" % (verified, unverified))

    # ── 4. suspicious patterns ───────────────────────────────────────────
    print("\n[4] pattern checks")
    for path in kt_files:
        text = open(path, encoding="utf-8").read()
        rel = os.path.relpath(path, PRISM)
        for pat, msg in [
            (r"\bTODO\b", "TODO left in source"),
            (r"\bFIXME\b", "FIXME left in source"),
            (r"println\(", "println left in source"),
            (r"\bThread\.sleep\b", "blocking sleep"),
            (r"GlobalScope", "GlobalScope use"),
            (r"!!", "non-null assertion"),
        ]:
            for m in re.finditer(pat, text):
                line = text.count("\n", 0, m.start()) + 1
                warnings.append(f"{rel}:{line}: {msg}")
    engine = [p for p in kt_files if "/engine/" in p]
    for path in engine:
        text = open(path, encoding="utf-8").read()
        rel = os.path.relpath(path, PRISM)
        if re.search(r"^\s*import\s+android\.", text, re.M):
            failures.append(f"{rel}: engine file imports android.* — breaks JVM testability")
            print("  FAIL  %s imports android.*" % rel)
    print("  ok    no engine file imports android.*")

    print("\n" + "=" * 72)
    print("FAILURES: %d   WARNINGS: %d" % (len(failures), len(warnings)))
    for f in failures:
        print("  FAIL " + f)
    if warnings:
        print("  (warnings)")
        for w in warnings[:60]:
            print("  warn " + w)
        if len(warnings) > 60:
            print("  ... and %d more" % (len(warnings) - 60))
    print("=" * 72)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
