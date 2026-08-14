"""Supervisor — keeps the server alive forever.

Runs server.py as a child process, streams its logs to the console, and
restarts it automatically if it ever crashes (self-repair).

    python3 run.py                          # default host/port
    python3 run.py --port 8080              # extra args are forwarded
"""
import subprocess
import sys
import time


def main() -> None:
    cmd = [sys.executable, "server.py", *sys.argv[1:]]
    backoff = 3
    while True:
        print(f"[supervisor] ▶ starting: {' '.join(cmd)}", flush=True)
        proc = subprocess.Popen(cmd)
        code = proc.wait()
        print(f"[supervisor] ✖ server exited with code {code} — "
              f"restarting in {backoff}s", flush=True)
        time.sleep(backoff)
        backoff = min(backoff * 2, 30)  # never hammer-restart too fast


if __name__ == "__main__":
    main()
