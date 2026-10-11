# Save-rollback reproduction (2026-10-11)

TEST TOOLING ONLY. This branch (`repro/save-rollback-2026-10-11`) is **not for merge**. It is
`smoke/v2194` (d2b012332, Argosy 2.19.4) plus:

- commit `37a3ca65c`: a **test shim** that treats the Android emulator's virtual second screen
  ("Emulator 2D Display") as a built-in panel so the dual-screen companion runs, and `ReproTrace`
  caller-stack logging in `PlaySessionTracker.endSession` / `endSessionInBackground` /
  `cancelSession` and `SecondaryHomeActivity.refocusSelf`. Both are diagnostics, not fixes.
- this folder: `repro.sh`, a throwaway local RomM (`docker-compose.yml`), and scrubbed log excerpts.

What it reproduces, on an Android emulator with two displays:

1. Argosy ends the play session of a game it just launched while the external emulator keeps running
   (issues: see "Save rollback on the Thor, 2026-10-11" in this fork).
2. The consequence: the in-game save made after that is not cached, Argosy's "active" cache stays old,
   and the next launch from Argosy restores the old cache over the newer disk save
   (`SaveSyncOrchestrator.checkDiskAgainstActive`, Secure Saves on).

## One-time setup

- **AVD** `argosy_repro`: android-34 google_apis_playstore x86_64, 1920x1080 landscape, 320 dpi.
  `repro.sh` only ever drives serial `emulator-5580` and refuses anything that is not an emulator.
- **Argosy APK** from this branch:
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug -PallAbis`
  (needs the submodules, including `sigil/third_party/*`).
- **External emulator**: the runs used Minarch Link (`farm.fuhry.minarch`), launched by Argosy with a
  plain VIEW intent. Any external emulator that writes a battery save to a known path should do;
  override `EMU=<package>` and `SAV=<save path on /sdcard>` (triggers `c`, `e` also start
  `$EMU/.MinarchActivity` to return to the game, adjust for another emulator).
- **Local RomM** (never a real server):
  ```sh
  export REPRO_DB_ROOT_PW=... REPRO_DB_PW=... ROMM_AUTH_SECRET_KEY=$(openssl rand -hex 32)
  mkdir -p library/roms/gba   # put your own dump of Pokemon LeafGreen (USA, Europe) (Rev 1) here
  docker compose up -d        # RomM 5.3.1 on 127.0.0.1:8095 (10.0.2.2:8095 from the emulator)
  docker compose exec -w /backend romm python -c 'import asyncio;from endpoints.sockets.scan import scan_platforms;asyncio.run(scan_platforms([],[],launchbox_remote_enabled=False))'
  ```
  Create a user in RomM, then pair Argosy and approve the device code (web UI, or
  `POST /api/auth/device/approve`).
- **Argosy settings**: Save Sync on, Secure Saves on (default), Dual-screen on, Screen Dimmer off,
  per-game emulator = the external emulator, Argosy = HOME role; grant usage access, all-files access
  and overlay (appops). Leave `secure theme_customization_overlay_packages` at its default
  `color_source=lock_wallpaper`: the lock-wallpaper recolor is what relaunches `MainActivity` in `a0`.
- Download LeafGreen in Argosy. Save an emulator snapshot named `repro-ready` in this state.
- **Saves**: put four different valid saves of the game next to `repro.sh` as `save_A.sav` ..
  `save_D.sav` (from your own play; not in git). `consequence` uses A (the "old" save) and C (the
  "newer" in-game save).

## Running

```sh
./repro.sh up              # RomM + emulator from snapshot + second display
./repro.sh a0              # or a1 a1late a2 b c d e f, or: consequence a0 | consequence f
./repro.sh all             # every trigger, then both consequences
./repro.sh down
```

Each run writes `out/<name>.log` (full logcat) and `out/<name>.key.log` (the relevant lines).

| Trigger | What it does | Session-ending caller seen (2.19.4) |
|---|---|---|
| `a0` | screen off/on (Argosy puts lock-screen art up), then launch from the bottom screen. The launch swaps in the game's art, recolors the theme, `Config changes=80000000`, `MainActivity` + companion relaunch | `MainActivity.cleanupStaleSession` (`MainActivity.kt:757`) -> `DualScreenManager.endSessionAfterEmulatorLeft` (`:1807`), ~1.5 s after "Session started". Competing caller: `MainActivity.kt:444` postDelayed -> `refocusSelf` -> `HomeViewModel.onResume` (`:2167`) -> `GameLaunchDelegate.handleSessionEnd`, which `cancelSession()`s a <30 s session (`:579`) |
| `a1` | same config change forced by a theme palette change 1 s after launch | same as `a0` |
| `a1late` | same, 45 s into play | same (`cleanupStaleSession`) |
| `a2` | second display removed and re-added | not reproduced |
| `b` | companion brought to front over the game | not reproduced |
| `c` | Home, then back to the still-running game (Recents) | `cleanupStaleSession` -> `endSessionAfterEmulatorLeft`; not restarted on return |
| `d` | re-pick the running game | session ended then a new one started (expected) |
| `e` | Argosy process killed under the running game, Argosy recreated | `ArgosyViewModel.scheduleStartupTasks` (`ArgosyViewModel.kt:391`) |
| `f` | Android Settings opened over the game for 10 s, then closed | `GameSessionService` presence watch (`GameSessionService.kt:283`); not restarted on return |

**PASS(reproduced)** for a trigger means: after the trigger the emulator's game process is still
alive, the log has "Session started" and then "Session ended" (or "Cancelled") for that session, and
`GameSessionService` is not running. The output line names the caller chain from `ReproTrace`:

```
PASS(reproduced) a0: game pid=<pid> alive, Argosy session ended (service=no has_session=<..>) via: [companion refocusSelf] ... | [endSessionInBackground(active=1)] DualScreenManager.endSessionAfterEmulatorLeft(DualScreenManager.kt:1807) MainActivity$cleanupStaleSession$1.invokeSuspend(MainActivity.kt:757) | ...
```

`FAIL` means the session survived the trigger (which is what a fix should produce).

**PASS(reproduced)** for `consequence a0|f` means: a clean launch/quit makes save A Argosy's
active cache; the session is then lost by trigger a0 or f; save C is pushed while the game runs
(standing in for an in-game save); re-picking the game in Argosy logs

```
[SaveSync] DISK gameId=1 | disk differs from active cache id=1, restoring it (Secure Saves on)
```

and the disk save is A again (C was rolled back). Argosy does keep C as a rollback cache entry.

## Log excerpts

- `thor-logcat-excerpt.txt`: the real device, 11:42:16-18 on 2026-10-11 (lock wallpaper rewritten,
  `Config changes=80000000`, game started, session ended ~1 s later, "no active session").
- `emulator-logcat-excerpt.txt`: the same chain on the emulator for `a0`, `f`, `e` and both
  consequences.

All excerpts are scrubbed (no server URL, device id, LAN address, tokens or personal data).
