# Repopox Local Agent 🖥️

This is the software that runs **on your PC**. It connects your real
desktop to the Arena agent (me) through GitHub.

```
[ this agent on your PC ]  <── poll ──>  [ GitHub repo (this one) ]  <── gh ──>  [ Arena agent (me) ]
        runs commands                          issues = mailbox                  writes commands
        posts results back
```

- **No pip installs.** Uses only Python's standard library (3.8+).
- **Secure:** every command is **signed** with your secret (HMAC-SHA256).
  The agent only runs correctly signed commands — anything else is rejected
  and closed. Keep this repo **private** (it should be).
- **Full-auto:** anything that passes the signature check runs immediately.
- **Rate-limit friendly:** polling uses ETag/304 conditional requests — an
  idle mailbox costs **zero** GitHub rate-limit budget.

---

## Setup (one time, ~5 minutes)

### 1. Pull the latest code

In a terminal, inside this repo folder:

```
git fetch origin
git checkout arena/275730f5-repopoxrev
```

### 2. Create a GitHub token for the agent

1. Go to https://github.com/settings/tokens (fine-grained) → **Generate new token**
2. Token name: `repopox-agent`
3. Expiration: whatever you like (e.g. 90 days)
4. **Repository access:** only this repo (`Adarshukumar/RepopoxRev`)
5. **Permissions → Repository permissions → Issues:** `Read and write`
   (nothing else — keep it minimal)
6. Click **Generate token** and **copy it** (it's shown only once)

### 3. Configure & start the agent

```
python local-agent\agent.py
```

First run it asks you for:

| Prompt | What to paste |
|---|---|
| relay repo | just press **Enter** (default is this repo) |
| GitHub token | the token from step 2 |
| relay secret | the secret shown in the Arena chat *(auto-loads if `local-agent\relay.secret` exists — I commit it once the repo is private)* |
| default workdir | press Enter for your home folder, or type a folder path |

It checks the token, runs a self-test, and saves the config to
`%USERPROFILE%\.repopox\agent.json` (never shared, never committed).

After setup, start it for real:

```
python local-agent\agent.py
```

Leave that window open — it's now **alive and watching**. It prints a line
for every command it runs and every result it posts.

---

## Everyday usage

```
python local-agent\agent.py           start / keep running (Ctrl+C stops)
python local-agent\agent.py status    check token + recent issues
python local-agent\agent.py setup     re-enter token / secret / workdir
python local-agent\agent.py once      single poll, then exit (debugging)
```

To make it survive restarts automatically (optional): create a scheduled
task or a shortcut that runs `pythonw local-agent\agent.py`.

---

## How a command flows

1. I (Arena agent) create an issue:
   - title: `repopox-cmd: <name>`
   - body: `{"payload": {name, cmd, cwd?, timeout?, ts, nonce}, "sig": "<hmac>"}`
2. Agent polls every 5 s (ETag conditional — free when idle), verifies
   `sig` with your secret, checks freshness (6 h replay guard).
3. Agent runs `cmd` in PowerShell (default) on your PC.
4. Agent posts the result (exit code, stdout, stderr, time) as a comment and
   closes the issue.
5. I read that comment (you can also just ping me: *"bro, check the result"*).

Payload fields (all optional except `cmd`):

| field | default | meaning |
|---|---|---|
| `cmd` | — | the command line to run |
| `name` | "" | short label, shows in the result comment |
| `cwd` | your workdir | working directory for the command |
| `timeout` | 300 (max 600) | seconds before the command is killed |

---

## Security notes (read once)

- Your **secret** lives only in `%USERPROFILE%\.repopox\agent.json` (and,
  once the repo is private, in `local-agent\relay.secret`). It is never
  sent anywhere and the repo must stay **private** for that file to exist.
- Commands are **HMAC-signed**: only correctly signed commands run. Unsigned
  or tampered issues are rejected, commented, and closed.
- It's **full-auto**: a correctly signed command runs without asking you.
  If you ever want to rotate the secret, tell me — I'll use the new one,
  and you re-run `python local-agent\agent.py setup`.
- **Kill switches:** `Ctrl+C` stops the agent instantly · revoking the token
  cuts it off from the mailbox · deleting `%USERPROFILE%\.repopox` resets it.
- Output caps: 20,000 chars per result · 4,000 char command cap · 600 s max
  runtime per command.
