#!/usr/bin/env python3
"""
Repopox Local Agent
===================
Runs on YOUR Windows PC. Watches the GitHub relay repo (this repo) for
command issues, verifies their signature, runs them locally, and posts
the results back as comments.

Standard library only. Python 3.8+. NO pip installs needed.

Usage:
  python agent.py           start the agent (runs forever, Ctrl+C to stop)
  python agent.py setup     (re)configure token / secret / workdir
  python agent.py status    check config + list recent issues
  python agent.py once      poll one time, then exit (debug)

A command issue looks like:
  title: repopox-cmd: <name>
  body:  {"payload": { ...json... }, "sig": "<hex hmac-sha256>"}

Only commands signed with YOUR secret are executed. Everything else is
rejected, commented on, and closed.
"""

import getpass
import hashlib
import hmac
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from urllib import request, error

API = "https://api.github.com"
POLL_SECONDS = 5
MAX_OUTPUT = 20000          # truncate posted stdout/stderr at this many chars
DEFAULT_TIMEOUT = 300       # per-command timeout, seconds
MAX_TIMEOUT = 600           # hard cap, seconds
MAX_CMD_LEN = 4000          # refuse longer commands
STALE_AFTER = 6 * 3600      # refuse commands older than this (replay guard)
TITLE_PREFIX = "repopox-cmd:"

CONFIG_DIR = Path(os.environ.get("USERPROFILE") or Path.home()) / ".repopox"
CONFIG_PATH = CONFIG_DIR / "agent.json"
SEEN_PATH = CONFIG_DIR / "seen.json"

DEFAULTS = {
    "repo": "Adarshukumar/RepopoxRev",
    "token": "",
    "secret": "",
    "workdir": "",
    "shell": "powershell",
}


def log(msg):
    print("[%s] %s" % (time.strftime("%H:%M:%S"), msg), flush=True)


# ---------------------------------------------------------------- config

def save_config(cfg):
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_PATH.write_text(json.dumps(cfg, indent=2), encoding="utf-8")
    try:
        os.chmod(CONFIG_PATH, 0o600)
    except OSError:
        pass


def load_config(must=False):
    try:
        cfg = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        return {**DEFAULTS, **cfg}
    except Exception:
        if must:
            log("no config found (%s) - run:  python agent.py setup" % CONFIG_PATH)
            sys.exit(1)
        return None


def load_seen():
    try:
        return set(json.loads(SEEN_PATH.read_text(encoding="utf-8")))
    except Exception:
        return set()


def save_seen(seen):
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    SEEN_PATH.write_text(json.dumps(sorted(seen)), encoding="utf-8")


# ---------------------------------------------------------------- github

def api(cfg, path, method="GET", data=None):
    """Small GitHub REST helper. Returns (status_code, parsed_body)."""
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "repopox-local-agent",
        "Authorization": "Bearer " + cfg["token"],
    }
    body = None
    if data is not None:
        body = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = request.Request(API + path, data=body, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=30) as r:
            raw = r.read().decode("utf-8", "replace")
            return r.status, (json.loads(raw) if raw else None)
    except error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw
    except Exception as e:
        return -1, str(e)


# ------------------------------------------------------- signing (HMAC)

def _canonical(payload_obj):
    return json.dumps(payload_obj, sort_keys=True, separators=(",", ":"))


def sign(secret, payload_obj):
    canonical = _canonical(payload_obj)
    sig = hmac.new(secret.encode("utf-8"), canonical.encode("utf-8"),
                   hashlib.sha256).hexdigest()
    return sig, canonical


def verify(secret, body):
    """Check an issue body. Returns (payload_dict, None) or (None, reason)."""
    try:
        doc = json.loads(body)
        sig = doc.get("sig", "")
        payload = doc.get("payload")
        if isinstance(payload, str):
            payload = json.loads(payload)
        if not isinstance(payload, dict):
            return None, "bad payload"
        expect = hmac.new(secret.encode("utf-8"), _canonical(payload).encode("utf-8"),
                          hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expect, sig):
            return None, "bad signature (only signed commands run)"
        ts = payload.get("ts")
        if not isinstance(ts, (int, float)) or abs(time.time() - ts) > STALE_AFTER:
            return None, "stale command (older than %dh)" % (STALE_AFTER // 3600)
        return payload, None
    except Exception as e:
        return None, "bad payload: %s" % e


# ------------------------------------------------------------- execution

def run_cmd(cfg, cmd, cwd, timeout):
    shell = (cfg.get("shell") or "powershell").strip()
    if shell.lower() in ("powershell", "pwsh"):
        argv = [shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd]
    else:
        argv = [shell, "-c", cmd]
    kwargs = {"capture_output": True, "text": True, "timeout": timeout, "cwd": cwd}
    if sys.platform == "win32":
        kwargs["creationflags"] = subprocess.CREATE_NO_WINDOW
    t0 = time.time()
    try:
        p = subprocess.run(argv, **kwargs)
        return {
            "exit_code": p.returncode,
            "stdout": p.stdout or "",
            "stderr": p.stderr or "",
            "error": None,
            "secs": round(time.time() - t0, 1),
        }
    except subprocess.TimeoutExpired as e:
        so, se = e.stdout or b"", e.stderr or b""
        so = so.decode("utf-8", "replace") if isinstance(so, bytes) else str(so)
        se = se.decode("utf-8", "replace") if isinstance(se, bytes) else str(se)
        return {
            "exit_code": None, "stdout": so, "stderr": se,
            "error": "timed out after %ss" % timeout,
            "secs": round(time.time() - t0, 1),
        }
    except Exception as e:
        return {
            "exit_code": None, "stdout": "", "stderr": "",
            "error": str(e), "secs": round(time.time() - t0, 1),
        }


def clip(s, n=MAX_OUTPUT):
    s = s or ""
    if len(s) > n:
        return s[:n] + "\n... [truncated %d chars]" % (len(s) - n)
    return s


def build_comment(payload, result):
    name = payload.get("name", "")
    cmd = payload.get("cmd", "")
    lines = ["## repopox result — %s" % (name or "command"), "```cmd", cmd, "```", ""]
    if result.get("error"):
        lines += ["**!! error:** %s" % result["error"], ""]
    lines += ["**exit code:** `%s`   **time:** %ss" % (result.get("exit_code"),
                                                       result.get("secs")), ""]
    so = clip(result.get("stdout")).strip()
    se = clip(result.get("stderr")).strip()
    if so:
        lines += ["**stdout:**", "```", so, "```", ""]
    if se:
        lines += ["**stderr:**", "```", se, "```", ""]
    if not so and not se and not result.get("error"):
        lines += ["*(no output)*", ""]
    lines.append("_run by repopox-local-agent @ %s_" % time.strftime("%Y-%m-%d %H:%M:%S"))
    return "\n".join(lines)


def process_issue(cfg, issue):
    repo, n = cfg["repo"], issue["number"]

    def close(note=None):
        if note:
            api(cfg, "/repos/%s/issues/%d/comments" % (repo, n), "POST", {"body": note})
        api(cfg, "/repos/%s/issues/%d" % (repo, n), "PATCH",
            {"state": "closed", "state_reason": "completed"})

    payload, err = verify(cfg["secret"], issue.get("body") or "")
    if err:
        log("#%d REJECTED: %s" % (n, err))
        close("rejected by agent: %s" % err)
        return

    cmd = str(payload.get("cmd", ""))
    if not cmd.strip():
        log("#%d REJECTED: empty cmd" % n)
        close("rejected by agent: empty cmd")
        return
    if len(cmd) > MAX_CMD_LEN:
        log("#%d REJECTED: cmd too long (%d > %d)" % (n, len(cmd), MAX_CMD_LEN))
        close("rejected by agent: cmd too long")
        return

    cwd = payload.get("cwd") or cfg.get("workdir") or str(Path.home())
    try:
        timeout = int(payload.get("timeout", DEFAULT_TIMEOUT))
    except Exception:
        timeout = DEFAULT_TIMEOUT
    timeout = max(5, min(timeout, MAX_TIMEOUT))

    log("#%d RUN (%s):\n     $ %s" % (n, payload.get("name", ""), cmd[:200]))
    result = run_cmd(cfg, cmd, cwd, timeout)
    log("#%d done, exit=%s in %ss" % (n, result.get("exit_code"), result.get("secs")))
    close(build_comment(payload, result))


# ----------------------------------------------------------------- poll

def poll_once(cfg, seen):
    """One poll cycle. Returns False if the token is dead, else True."""
    status, issues = api(cfg, "/repos/%s/issues?state=open&per_page=50&sort=created&direction=desc"
                         % cfg["repo"])
    if status == 401:
        log("token rejected (401) - re-run:  python agent.py setup")
        return False
    if status != 200:
        log("poll failed (%s): %s" % (status, str(issues)[:200]))
        return True

    for issue in issues or []:
        if "pull_request" in issue:
            continue
        if not str(issue.get("title", "")).startswith(TITLE_PREFIX):
            continue
        n = issue["number"]
        if n in seen:
            continue
        seen.add(n)
        save_seen(seen)
        try:
            process_issue(cfg, issue)
        except Exception as e:
            log("#%d FAILED: %s" % (n, e))
            api(cfg, "/repos/%s/issues/%d/comments" % (cfg["repo"], n), "POST",
                {"body": "agent error: %s" % e})
            api(cfg, "/repos/%s/issues/%d" % (cfg["repo"], n), "PATCH", {"state": "closed"})
    return True


def main_loop(cfg):
    seen = load_seen()
    log("repopox agent watching  %s" % cfg["repo"])
    log("default workdir:        %s" % (cfg.get("workdir") or str(Path.home())))
    log("shell:                  %s" % (cfg.get("shell") or "powershell"))
    log("poll every %ds - Ctrl+C to stop" % POLL_SECONDS)
    tick = 0
    while True:
        try:
            if not poll_once(cfg, seen):
                return
        except Exception as e:
            log("poll error: %s" % e)
        tick += 1
        if tick % 12 == 0:
            log("... still watching (poll #%d)" % tick)
        time.sleep(POLL_SECONDS)


# ------------------------------------------------------------- commands

def setup():
    log("=== repopox agent setup ===")
    cfg = load_config(must=False) or dict(DEFAULTS)
    repo = input("relay repo [%s]: " % (cfg.get("repo") or DEFAULTS["repo"])).strip() \
        or DEFAULTS["repo"]
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repo):
        log("that doesn't look like owner/repo - aborting")
        sys.exit(1)
    token = getpass.getpass("GitHub token (hidden): ").strip()
    secret = getpass.getpass("relay secret (hidden): ").strip()
    workdir = input("default workdir [%s]: " % (cfg.get("workdir") or str(Path.home()))).strip() \
        or str(Path.home())
    if not token or not secret:
        log("both the token and the secret are required - aborting")
        sys.exit(1)

    new = {**DEFAULTS, "repo": repo, "token": token, "secret": secret, "workdir": workdir}
    status, me = api(new, "/user")
    if status != 200:
        log("token check failed: %s" % str(me)[:200])
        sys.exit(1)
    status, repo_obj = api(new, "/repos/" + repo)
    if status != 200:
        log("repo check failed for %s: %s" % (repo, str(repo_obj)[:200]))
        sys.exit(1)
    # HMAC self-test: prove the stored secret round-trips
    test_payload = {"name": "selftest", "cmd": "echo selftest",
                    "ts": int(time.time()), "nonce": "selftest"}
    sig, _ = sign(secret, test_payload)
    ok, why = verify(secret, json.dumps({"payload": test_payload, "sig": sig}))
    if ok is None:
        log("HMAC self-test failed: %s" % why)
        sys.exit(1)

    save_config(new)
    log("OK! logged in as %s, repo %s reachable, HMAC self-test passed." %
        (me.get("login"), repo))
    log("config saved to %s" % CONFIG_PATH)
    log("now start the agent:  python agent.py")


def show_status(cfg):
    status, me = api(cfg, "/user")
    if status == 200:
        log("token OK (logged in as %s)" % me.get("login"))
    else:
        log("token FAILED: %s" % str(me)[:200])
    status, issues = api(cfg, "/repos/%s/issues?per_page=8&sort=created&direction=desc"
                         % cfg["repo"])
    if status != 200:
        log("repo check failed: %s" % status)
        return
    log("recent issues in %s:" % cfg["repo"])
    for i in issues or []:
        if "pull_request" in i:
            continue
        log("  #%d [%s] %s" % (i["number"], i["state"], i["title"][:80]))
    log("workdir: %s" % (cfg.get("workdir") or str(Path.home())))
    log("shell:   %s" % (cfg.get("shell") or "powershell"))


def main():
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if arg in ("-h", "--help", "help"):
        print(__doc__)
        return
    if arg == "setup":
        setup()
        return
    if arg == "status":
        show_status(load_config(must=True))
        return
    if arg == "once":
        poll_once(load_config(must=True), load_seen())
        return
    if arg:
        log("unknown arg: %s" % arg)
        print(__doc__)
        sys.exit(1)

    cfg = load_config(must=False)
    if cfg is None:
        log("no config yet - running setup first...")
        setup()
        cfg = load_config(must=True)
    try:
        main_loop(cfg)
    except KeyboardInterrupt:
        log("stopped. bye!")


if __name__ == "__main__":
    main()
