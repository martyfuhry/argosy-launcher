---
name: debug-device
description: Drive the connected debug build on a real device - install, fetch logs, send gamepad/keyboard input, grab screenshots. Use whenever testing a change on-device, reproducing a bug, or verifying UI behavior so the adb workflow is one approved command instead of many prompts.
---

# Driving the debug app on-device

All on-device testing for Argosy targets the **debug** build, never the release app.
`scripts/argosy-dev.sh` wraps the whole adb workflow behind one command.

---

## NON-NEGOTIABLE: test the .debug package only

- The install-and-test target is **`com.nendo.argosy.debug`** (applicationIdSuffix `.debug`).
- The release app **`com.nendo.argosy`** is a *separate install* that may be on the same
  device, connected to a *different* RomM server, with its own logs and pid. Never read its
  logs or pid when verifying a change - it will show stale/unrelated data and send you
  chasing ghosts. (Real example: connecting against `4.9.0-beta.2` in the release logs while
  the debug build was on a `5.0-prerelease` server - a wasted detour.)
- The script hardcodes the debug package everywhere (`pid`, `logs`, `launch`, `install`).
  If you ever run raw adb, filter by the debug pid: `pidof com.nendo.argosy.debug`.

## The toolset

`scripts/argosy-dev.sh <command>`:

- `devices` - list attached devices.
- `pid` - debug-app pid (empty if not running).
- `install [--build]` - install `app/build/outputs/apk/debug/app-debug.apk`. `--build` runs
  `assembleDebug` first and blocks; usually you build in the background separately (builds are
  slow) and then just `install`.
- `launch` - wake the screen and foreground the debug app.
- `logs [-c] [pattern]` - dump logcat for the debug pid. `-c` clears the buffer first (do this
  *before* a repro so the next dump is clean and nothing rolls off under log spam). `pattern`
  is an `-iE` regex, e.g. `logs "heartbeat|connect:|FirstRunWizard"`.
- `key <name|code> ...` - send key events. Names: `up down left right a b confirm back enter
  menu home wake`. (`a`/`confirm` = 96, `b` = 97; numeric codes also accepted.)
- `text <string>` - type into the focused field.
- `shot [path]` - screenshot to `path` (default `/tmp/argosy-shot.png`); prints the path so
  you can Read it immediately.

Set `ARGOSY_DEVICE=<serial>` to target a specific device when several are attached
(e.g. Odin3 vs Thor); otherwise the single connected device is used.

## Driving the UI without the user

The app is gamepad-driven, so you can reproduce flows yourself instead of asking for repros:

1. `argosy-dev.sh logs -c` - clear the buffer.
2. `argosy-dev.sh launch` then `shot` - see where you are.
3. Navigate with `key down` / `key up` / `key left` / `key right`, confirm with `key a`,
   back with `key b`. Enter text fields with `key a` then `text "..."`.
4. `shot` after each step to confirm focus/state visually - the focus highlight is the source
   of truth, not your assumption about which control is selected.
5. `logs "<tags>"` to confirm what fired (e.g. `commitUrl`, `connect:`, `beginDeviceAuth`).

Ground truth is the screenshot + the debug-pid log, in that order. When behavior and your
mental model disagree, trust the screenshot and the log - not the theory.

## Guardrails

- Never `pm clear`, `rm` app data, or delete DataStore/DB files to "reset" - ask first.
  To re-enter first-run or reset state, ask the user.
- `logs -c` only clears the logcat ring buffer (safe), not app data.
- Builds are slow; run `assembleDebug` in the background and `install` after it finishes,
  rather than `install --build` which blocks the turn.
