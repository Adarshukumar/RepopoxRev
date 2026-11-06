"""
kotlin_lex.py -- a small, faithful Kotlin source lexer used by verify.py.

Its one job is to answer: "which characters of this file are *code*, and which are
inside a comment or a string literal?" Everything that is not code is replaced by a
space (newlines are preserved) so downstream checks can count brackets and match
identifiers without being fooled by text.

Why it has to be a state machine and not a regex pass: Kotlin nests.

  * block comments nest          /* a /* b */ c */
  * string templates nest        "outer ${ if (x) "inner ${y}" else "z" } tail"
  * raw strings hold templates   \"\"\" ${ map["k"] } \"\"\"
  * char literals look like text '\'', '{'

The subtle part is the template. While we are inside "${ ... }" we are in *code*, and
that code may itself open a string, which may itself open a template, and so on. So the
frame a "${" creates must (a) *replace* the string frame rather than sit on top of it --
otherwise a quote inside the template is misread as the end of the outer string -- and
(b) remember which string state to resume when its matching "}" arrives. That resume
token is the third element of each stack entry.
"""

from __future__ import annotations

CODE, LINE, BLOCK, CHAR, STR, RAW, TICK = "code", "line", "block", "char", "str", "raw", "tick"

# a stack entry is (kind, brace_depth, resume_after_template)
Frame = tuple[str, int, str | None]


def mask_kotlin(src: str) -> str:
    """Return `src` with every non-code character blanked, length and newlines intact."""
    out = list(src)
    n = len(src)
    i = 0
    # brace_depth -1 is the file's own sentinel: it can never reach 0, so the frame is never
    # popped by a stray "}". Template frames start at 0, so their first "}" at depth 0 closes
    # the template.
    stack: list[Frame] = [(CODE, -1, None)]

    def blank(j: int, k: int) -> None:
        for idx in range(j, min(k, n)):
            if out[idx] != "\n":
                out[idx] = " "

    while i < n:
        kind, depth, resume = stack[-1]
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""

        # ---------------- line comment ----------------
        if kind == LINE:
            if c == "\n":
                stack.pop()
            else:
                out[i] = " "
            i += 1
            continue

        # ---------------- nestable block comment ----------------
        if kind == BLOCK:
            if c == "/" and nxt == "*":
                stack.append((BLOCK, 0, None))
                blank(i, i + 2)
                i += 2
            elif c == "*" and nxt == "/":
                blank(i, i + 2)
                i += 2
                stack.pop()
            else:
                if c != "\n":
                    out[i] = " "
                i += 1
            continue

        # ---------------- `backtick identifier` ----------------
        # Test and DSL names live in backticks: `the artist's own copy beats a tribute act`.
        # Their contents are an identifier, not code, so an apostrophe inside one must not open
        # a char literal -- without this state the lexer desyncs on the first `'` and every
        # bracket after it is counted in the wrong context.
        if kind == TICK:
            if c == "`":
                out[i] = " "
                i += 1
                stack.pop()
            elif c == "\n":
                stack.pop()
            else:
                out[i] = " "
                i += 1
            continue

        # ---------------- char literal ----------------
        if kind == CHAR:
            if c == "\\":
                blank(i, i + 2)
                i += 2
            elif c == "'" or c == "\n":
                if c == "'":
                    out[i] = " "
                    i += 1
                stack.pop()
            else:
                out[i] = " "
                i += 1
            continue

        # ---------------- "..." string ----------------
        if kind == STR:
            if c == "\\":
                blank(i, i + 2)
                i += 2
            elif c == '"':
                out[i] = " "
                i += 1
                stack.pop()
            elif c == "$" and nxt == "{":
                stack.pop()                      # leave the string ...
                stack.append((CODE, 0, STR))     # ... and resume it after the template
                out[i] = " "
                out[i + 1] = "{"                 # keep the "{" : it is a real code bracket and
                i += 2                           # must balance the "}" that ends the template
            elif c == "\n":                      # unterminated: resync at the newline
                stack.pop()
            else:
                out[i] = " "
                i += 1
            continue

        # ---------------- raw \"\"\"...\"\"\" string ----------------
        if kind == RAW:
            if src.startswith('"""', i):
                # count the whole run: 3 quotes close the string, 4+ means the extras were the
                # string's own last characters (Regex("""content="([^"]+)"""") is exactly that)
                run = 0
                while i + run < n and src[i + run] == '"':
                    run += 1
                blank(i, i + run)
                i += run
                stack.pop()
            elif c == "$" and nxt == "{":
                stack.pop()
                stack.append((CODE, 0, RAW))
                out[i] = " "
                out[i + 1] = "{"                 # same rule as for "..." strings
                i += 2
            else:
                if c != "\n":
                    out[i] = " "
                i += 1
            continue

        # ---------------- code ----------------
        if c == "/" and nxt == "/":
            stack.append((LINE, 0, None))
            blank(i, i + 2)
            i += 2
            continue
        if c == "/" and nxt == "*":
            stack.append((BLOCK, 0, None))
            blank(i, i + 2)
            i += 2
            continue
        if src.startswith('"""', i):
            stack.append((RAW, 0, None))
            blank(i, i + 3)
            i += 3
            continue
        if c == '"':
            stack.append((STR, 0, None))
            out[i] = " "
            i += 1
            continue
        if c == "`":
            stack.append((TICK, 0, None))
            out[i] = " "
            i += 1
            continue
        if c == "'":
            stack.append((CHAR, 0, None))
            out[i] = " "
            i += 1
            continue
        if c == "{":
            stack[-1] = (CODE, depth + 1, resume)
            i += 1
            continue
        if c == "}":
            stack.pop()
            if depth > 0:                        # a plain nested block
                stack.append((CODE, depth - 1, resume))
            elif depth == 0 and resume:          # the "}" that ends a ${...} template
                stack.append((resume, 0, None))
            elif not stack:                      # stray "}" at file scope: keep the sentinel
                stack.append((CODE, -1, None))
            i += 1
            continue
        i += 1

    return "".join(out)


def balance(masked: str) -> dict[str, int]:
    """Bracket balance over masked source. Returns unclosed / mismatched / max depth."""
    pairs = {"{": "}", "(": ")", "[": "]"}
    closing = {v: k for k, v in pairs.items()}
    stack: list[str] = []
    mismatched = 0
    depth = 0
    max_depth = 0
    for ch in masked:
        if ch in pairs:
            stack.append(ch)
            depth += 1
            max_depth = max(max_depth, depth)
        elif ch in closing:
            if stack and stack[-1] == closing[ch]:
                stack.pop()
                depth -= 1
            else:
                mismatched += 1
                if stack:
                    stack.pop()
                    depth -= 1
    return {"unclosed": len(stack), "mismatched": mismatched, "max_depth": max_depth}


def first_anomaly(masked: str, raw: str) -> str | None:
    """Line number + text of the first bracket that cannot be matched, for a report."""
    pairs = {"{": "}", "(": ")", "[": "]"}
    closing = {v: k for k, v in pairs.items()}
    stack: list[tuple[str, int]] = []
    line = 1
    raw_lines = raw.splitlines()
    for ch in masked:
        if ch == "\n":
            line += 1
        elif ch in pairs:
            stack.append((ch, line))
        elif ch in closing:
            if stack and stack[-1][0] == closing[ch]:
                stack.pop()
            else:
                txt = raw_lines[line - 1].strip()[:150] if line - 1 < len(raw_lines) else ""
                return f"line {line}: unmatched '{ch}' -> {txt}"
    if stack:
        opener, at = stack[0]
        txt = raw_lines[at - 1].strip()[:150] if at - 1 < len(raw_lines) else ""
        return f"line {at}: '{opener}' never closed (EOF reached) -> {txt}"
    return None


def strip_gradle_comments(src: str) -> str:
    """.gradle.kts is Kotlin, so the same lexer strips its comments and strings."""
    return mask_kotlin(src)


if __name__ == "__main__":
    cases = [
        # (source, expected unclosed, expected mismatched)
        ('fun a() { val s = "x ${if (b) "y ${z}" else "w"} tail" }', 0, 0),
        ('/* a /* b */ c */ fun f() {}', 0, 0),
        ("val c = '}' ; fun g() { }", 0, 0),
        ("val r = \"\"\"${ map[\"k\"] } ok\"\"\"\nfun h() {}", 0, 0),
        ('fun broken() { ', 1, 0),
        ('@Suppress("a","b") class C { fun m() = listOf(1,2).map { it } }', 0, 0),
        ('val u = "http://a/${b.path}/c"; fun q() {}', 0, 0),
        ('val esc = "a\\"b{c"; fun w() {}', 0, 0),
        ('// } not code\nfun e() {}', 0, 0),
        ('val multi = """\n line { \n ${x}\n""" \nfun t() {}', 0, 0),
        # a backtick test name holding an apostrophe must not open a char literal
        ('@Test fun `the artist\'s own copy beats a tribute act`() { val x = listOf(1) }', 0, 0),
        # a raw string whose *content* ends with a quote char: 4 quotes in a row
        ('val re = Regex("""content="([^"]+)\"""")\nfun z() { }', 0, 0),
        # nested templates inside a backtick-free raw string
        ('val t = """a ${ "$b" } c"""; fun y() { }', 0, 0),
    ]
    failed = 0
    for src, eu, em in cases:
        got = balance(mask_kotlin(src))
        ok = got["unclosed"] == eu and got["mismatched"] == em
        failed += not ok
        print(("PASS " if ok else "FAIL "), repr(src[:58]), got)
    print("\nself-test:", "all pass" if not failed else f"{failed} failed")
    raise SystemExit(1 if failed else 0)
