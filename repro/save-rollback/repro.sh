#!/usr/bin/env bash
# repro.sh - reproduce "Argosy loses the session of a game it launched" on an Android emulator.
#
# Replication only. Runs against the emulator AVD `argosy_repro` (serial emulator-5580) and a
# local RomM 5.3.1 in docker (http://127.0.0.1:8095, reached from the emulator as 10.0.2.2:8095).
# It never talks to a real device or to the real RomM server.
#
# Usage:
#   repro.sh up          start RomM containers + emulator (from snapshot `repro-ready`) + 2nd display
#   repro.sh all         run every trigger, then the end-to-end stale-cache consequence
#   repro.sh <name>      run one: a0 a1 a1late a2 b c d e f | consequence [a0|f]
#   repro.sh down        stop the emulator and the RomM containers
#
# Setup that `up` expects (done once, see README notes at the bottom of this file):
#   - AVD argosy_repro (android-34 google_apis_playstore x86_64, 1920x1080 landscape, 320dpi)
#     with snapshot `repro-ready`: Argosy debug (repro/session-loss build) is HOME, paired to the
#     local RomM, Save Sync on, Secure Saves on (default), Dual-screen on, LeafGreen downloaded and
#     set to Minarch Link per game; Minarch Link installed.
#   - The RomM compose project `argosy-repro` (docker-compose.yml here).
#
# PASS(reproduced) = after the trigger the emulator process `farm.fuhry.minarch:game` is alive but
# Argosy has ended the session (log "Session ended" for the session it had just started, and no
# GameSessionService running).  FAIL = the session survived the trigger.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERIAL="${SERIAL:-emulator-5580}"
A=(adb -s "$SERIAL")
PKG=com.nendo.argosy.debug
EMU="${EMU:-farm.fuhry.minarch}"   # the external emulator package Argosy launches (Minarch Link here)
SAV="${SAV:-/sdcard/MinarchLink/Saves/GBA/Pokemon - LeafGreen Version (USA, Europe) (Rev 1).sav}"
SDK="${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}"
OUT="${OUT:-$HERE/out}"
mkdir -p "$OUT"

log()  { printf '[%s] %s\n' "$(date +%T)" "$*" >&2; }
sh_()  { "${A[@]}" shell "$@" 2>/dev/null | tr -d '\r'; }

guard_serial() {
  # Refuse to drive anything but the emulator.
  case "$SERIAL" in emulator-*) ;; *) echo "refusing non-emulator serial $SERIAL" >&2; exit 2;; esac
  [ "$(sh_ getprop ro.kernel.qemu)" = 1 ] || [ "$(sh_ getprop ro.boot.qemu)" = 1 ] || {
    echo "$SERIAL is not an emulator" >&2; exit 2; }
}

game_pid()     { sh_ pidof "$EMU:game"; }
svc_running()  { "${A[@]}" shell dumpsys activity services "$PKG" 2>/dev/null | grep -q "GameSessionService}" && echo yes || echo no; }
has_session()  { sh_ run-as "$PKG" cat shared_prefs/argosy_session_state.xml | grep -o 'name="has_session" value="[a-z]*"' | grep -o 'true\|false'; }
d2()           { "${A[@]}" shell dumpsys window displays 2>/dev/null | tr -d '\r' | grep -o "mDisplayId=[1-9][0-9]*" | head -1 | tr -dc 0-9; }
mark()         { "${A[@]}" logcat -c; }
dumplog()      { "${A[@]}" logcat -d -v threadtime > "$OUT/$1.log"; }
disk_md5()     { sh_ md5sum "\"$SAV\"" | awk '{print $1}'; }
md5_name()     { local m="$1"; for f in "$HERE"/save_?.sav; do [ "$(md5sum < "$f" | awk '{print $1}')" = "$m" ] && { basename "$f" .sav | sed 's/save_//'; return; }; done; echo "?($m)"; }

reset_clean() {
  # No emulator running, Argosy home on both screens, no live session.
  "${A[@]}" shell am force-stop "$EMU"
  sleep 2
  "${A[@]}" shell input keyevent 3
  sleep 4
  "${A[@]}" shell input -d "$(d2)" keyevent 3 2>/dev/null
  sleep 3
}

launch_from_argosy() {
  # The person's path: tap LeafGreen on the bottom (control) screen's home.
  "${A[@]}" shell input -d "$(d2)" tap 330 700
}

wait_session_started() {  # waits up to $1 s for "Session started"
  local t=${1:-25}
  for _ in $(seq 1 "$t"); do
    "${A[@]}" logcat -d | grep -q "Session started" && return 0
    sleep 1
  done
  return 1
}

chain() {  # first ReproTrace stacks -> "Caller(File.kt:N) <- ..." for the end + the refocus that preceded it
  awk '/ReproTrace: [a-zA-Z]/{n++; hdr=$0; sub(/.*ReproTrace: /,"",hdr); printf "%s[%s]", (n>1?" | ":""), hdr} /ReproTrace: \tat com\.nendo/{ if (k[n]++<4) { s=$0; sub(/.*\tat com\.nendo\.argosy\./,"",s); printf " %s", s } }' "$1" \
    | sed 's/(PlaySessionTracker.kt:[0-9]*)//g' | cut -c1-900
}

verdict() {  # name
  local name=$1 pid svc hs ended started
  sleep "${SETTLE:-6}"
  dumplog "$name"
  pid=$(game_pid); svc=$(svc_running); hs=$(has_session)
  started=$(grep -c "Session started" "$OUT/$name.log")
  ended=$(grep -cE "Session ended|Cancelled \(no save backup\)" "$OUT/$name.log")
  local why
  why=$(chain "$OUT/$name.log")
  if [ -n "$pid" ] && [ "$started" -gt 0 ] && [ "$ended" -gt 0 ] && [ "$svc" = no ]; then
    echo "PASS(reproduced) $name: game pid=$pid alive, Argosy session ended (service=$svc has_session=$hs) via: ${why:-?}"
  else
    echo "FAIL $name: game pid=${pid:-none}, started=$started ended=$ended service=$svc has_session=$hs"
  fi
  grep -E "Session started|Session ended|Cancelled|short session|proceeding with session end|Config changes=|showBeforeLaunch|ReproTrace|Companion resumed|Emulator left the screen|onResume: |PRE_LAUNCH|DISK gameId|restoring it|Stop requested|Force stopped" \
    "$OUT/$name.log" | cut -c1-230 > "$OUT/$name.key.log"
}

# ---------------------------------------------------------------- triggers
screen_cycle() {
  # Screen off -> Argosy puts its library mosaic on the lock screen (LockScreenArtManager screenOffReceiver);
  # the next launch then swaps in the game's hero art, which recolors the system theme.
  "${A[@]}" shell input keyevent 223; sleep 4
  "${A[@]}" shell input keyevent 224; sleep 1
  "${A[@]}" shell wm dismiss-keyguard; sleep 3
}

t_a0() {  # plain launch after the screen slept; Argosy's lock-screen art swaps the wallpaper -> config change 0x80000000 while launching
  reset_clean; screen_cycle; mark
  launch_from_argosy
  wait_session_started 30 || { echo "FAIL a0: no session started"; return; }
  verdict a0
}

assets_config_change() {
  # A system-wide assets-paths change (Config changes=80000000), the kind the Thor logged at 11:42:17.56.
  # It is what a theme recolor does (SystemUI applies new fabricated color overlays); here we set a
  # preset palette and then put the previous setting back. (Toggling a navbar overlay instead hung
  # the emulator's system_server once; don't.)
  local key=theme_customization_overlay_packages old c
  c=$(printf '%06X' $(( (RANDOM << 9 ^ RANDOM) & 0xFFFFFF )))
  old=$(sh_ settings get secure $key)
  "${A[@]}" shell settings put secure $key "'{\"android.theme.customization.system_palette\":\"$c\",\"android.theme.customization.accent_color\":\"$c\",\"android.theme.customization.theme_style\":\"TONAL_SPOT\",\"android.theme.customization.color_source\":\"preset\"}'"
  sleep 4
  if [ "$old" = null ]; then "${A[@]}" shell settings delete secure $key; else "${A[@]}" shell settings put secure $key "'$old'"; fi
}

t_a1() {  # assets-paths config change (MainActivity + companion relaunched) 1 s after the session starts, game still starting
  reset_clean; mark
  launch_from_argosy
  wait_session_started 30 || { echo "FAIL a1: no session started"; return; }
  sleep 1; assets_config_change
  verdict a1
}

t_a1late() {  # the same config change 45 s into play (session older than the 30 s "short session" cut)
  reset_clean; mark
  launch_from_argosy
  wait_session_started 30 || { echo "FAIL a1late: no session started"; return; }
  sleep 45; assets_config_change
  verdict a1late
}

t_a2() {  # second display detached and re-attached while the game is starting
  reset_clean; mark
  launch_from_argosy
  wait_session_started 30 || { echo "FAIL a2: no session started"; return; }
  "${A[@]}" emu multidisplay del 1 >/dev/null; sleep 2
  "${A[@]}" emu multidisplay add 1 1080 1240 320 0 >/dev/null; sleep 3
  verdict a2
  "${A[@]}" shell input keyevent 3; sleep 4   # let Argosy put its companion on the new display
}

t_b() {  # companion screen brought back to front while the game plays on top (what refocusSelf does: REORDER_TO_FRONT|SINGLE_TOP)
  reset_clean; mark
  launch_from_argosy
  wait_session_started 30 || { echo "FAIL b: no session started"; return; }
  sleep 40
  "${A[@]}" shell am start --display "$(d2)" -f 0x20020000 -n "$PKG/com.nendo.argosy.hardware.SecondaryHomeActivity" >/dev/null
  verdict b
}

t_c() {  # Argosy brought back from background after the emulator had the foreground a while
  reset_clean; mark
  launch_from_argosy
  wait_session_started 30 || { echo "FAIL c: no session started"; return; }
  sleep 30
  "${A[@]}" shell am start -W -c android.intent.category.HOME -a android.intent.action.MAIN >/dev/null
  sleep 4
  # back to the still-running game the way Recents would
  "${A[@]}" shell am start -W -n "$EMU/.MinarchActivity" >/dev/null 2>&1
  verdict c
}

t_d() {  # re-pick the same game while it is already running (game on top, launcher on the bottom)
  reset_clean; mark
  launch_from_argosy
  wait_session_started 30 || { echo "FAIL d: no session started"; return; }
  sleep 10
  "${A[@]}" logcat -c
  "${A[@]}" shell am start -a android.intent.action.VIEW -d "argosy://play/1" "$PKG" >/dev/null
  sleep 3
  wait_session_started 30
  verdict d
}

t_e() {  # startup tasks: Argosy's process dies under a running game and is recreated (scheduleStartupTasks)
  reset_clean; mark
  launch_from_argosy
  wait_session_started 30 || { echo "FAIL e: no session started"; return; }
  sleep 10
  local p; p=$(sh_ pidof "$PKG")
  "${A[@]}" shell run-as "$PKG" kill -9 "$p"
  sleep 3
  "${A[@]}" shell am start -W -c android.intent.category.HOME -a android.intent.action.MAIN >/dev/null
  sleep 4
  "${A[@]}" shell am start -W -n "$EMU/.MinarchActivity" >/dev/null 2>&1
  verdict e
}

t_f() {  # another app (Android Settings) opened over the running game, then the game brought back (Recents)
  reset_clean; mark
  launch_from_argosy
  wait_session_started 30 || { echo "FAIL f: no session started"; return; }
  sleep 40
  local before; before=$(game_pid)
  "${A[@]}" shell am start -a android.settings.SETTINGS >/dev/null; sleep 10
  "${A[@]}" shell am force-stop com.android.settings; sleep 4      # Settings gone -> the game task is on top again
  [ "$(game_pid)" = "$before" ] || log "f: game process changed ($before -> $(game_pid)): the game activity was destroyed"
  verdict f
}

lose_session() {  # a0 | f : leave the game running with Argosy's session gone
  case $1 in
    a0) screen_cycle; mark; launch_from_argosy; wait_session_started 30; sleep 12;;
    f)  mark; launch_from_argosy; wait_session_started 30; sleep 35
        "${A[@]}" shell am start -a android.settings.SETTINGS >/dev/null; sleep 10
        "${A[@]}" shell am force-stop com.android.settings; sleep 4;;
  esac
}

consequence() {  # [a0|f]
  # End to end: Argosy's active cache = save A. The session is lost while the game keeps running, the
  # game then writes a newer save (C), and the person re-picks the game in Argosy. Expected:
  # "disk differs from active cache ... restoring it (Secure Saves on)" and the disk back to A.
  local via=${1:-a0}
  reset_clean
  "${A[@]}" push "$HERE/save_A.sav" "$SAV" >/dev/null
  mark; launch_from_argosy; wait_session_started 30; sleep 12
  "${A[@]}" shell am force-stop "$EMU"; "${A[@]}" shell input keyevent 3; sleep 12   # clean quit: A cached + active
  dumplog "consequence-$via.0"
  log "baseline: disk=$(md5_name "$(disk_md5)"), Argosy active cache = that save"
  lose_session "$via"
  local lost=no; [ -n "$(game_pid)" ] && [ "$(svc_running)" = no ] && lost=yes
  log "session lost while the game runs ($via): $lost"
  "${A[@]}" push "$HERE/save_C.sav" "$SAV" >/dev/null          # the running game saves
  sleep 8
  local cached=no; "${A[@]}" logcat -d | grep -q "Live cache" && cached=yes
  local before; before=$(md5_name "$(disk_md5)")
  mark
  launch_from_argosy                                            # re-pick from the bottom screen
  wait_session_started 40; sleep 5
  dumplog "consequence-$via"
  local after; after=$(md5_name "$(disk_md5)")
  local line; line=$(grep -m1 "restoring it" "$OUT/consequence-$via.log" | sed 's/.*SaveSyncOrchestrator: //' | cut -c1-140)
  if [ "$lost" = yes ] && [ "$before" = C ] && [ "$after" = A ] && [ -n "$line" ]; then
    echo "PASS(reproduced) consequence-$via: session lost, in-game save C cached=$cached, re-pick put A back over C: $line"
  else
    echo "FAIL consequence-$via: lost=$lost cached=$cached disk before=$before after=$after restore-line='${line}'"
  fi
}

up() {
  (cd "$HERE" && REPRO_LIBRARY="${REPRO_LIBRARY:-$HERE/library}" docker compose up -d) >&2
  if ! "${A[@]}" get-state >/dev/null 2>&1; then
    nohup "$SDK/emulator/emulator" -avd argosy_repro -port 5580 -no-window -no-audio -no-boot-anim \
      -gpu swiftshader_indirect -snapshot repro-ready >"$OUT/emulator.log" 2>&1 &
    "${A[@]}" wait-for-device
    until [ "$(sh_ getprop sys.boot_completed)" = 1 ]; do sleep 2; done
  fi
  # The emulator's own "adb -e" call that starts the guest multidisplay service fails whenever another
  # TCP device is attached; start it ourselves on this serial only.
  "${A[@]}" shell am broadcast -a com.android.emulator.multidisplay.START \
    -n com.android.emulator.multidisplay/.MultiDisplayServiceReceiver --user 0 >/dev/null
  sleep 3
  "${A[@]}" emu multidisplay add 1 1080 1240 320 0 >/dev/null 2>&1 || true
  sleep 5
}

down() {
  "${A[@]}" emu kill >/dev/null 2>&1 || true
  (cd "$HERE" && docker compose stop) >&2
}

main() {
  local cmd=${1:-all}
  case "$cmd" in
    up) up; exit;;
    down) down; exit;;
  esac
  guard_serial
  case "$cmd" in
    all) for t in a0 a1 a1late a2 b c d e f; do "t_$t"; done; consequence a0; consequence f;;
    a0|a1|a1late|a2|b|c|d|e|f) "t_$cmd";;
    consequence) consequence "${2:-a0}";;
    *) echo "unknown: $cmd" >&2; exit 2;;
  esac
}
main "$@"

# ------------------------------------------------------------------------------------------------
# NOTES (one-time setup, done 2026-10-11; see README.md next to this file)
# - Not in git: library/ (your own LeafGreen dump), save_A..D.sav (four different saves from your own
#   play), out/, the APK.
# - Argosy APK: branch repro/save-rollback-2026-10-11 of this repo (= smoke/v2194 d2b012332 +
#   a TEST SHIM that treats the emulator's "Emulator 2D Display" as a built-in panel, + ReproTrace
#   caller-stack logging in PlaySessionTracker.endSession/endSessionInBackground/cancelSession and
#   SecondaryHomeActivity.refocusSelf). Build: JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
#   ./gradlew assembleDebug -PallAbis (submodules incl. sigil/third_party/* from a local checkout).
# - RomM: docker-compose.yml here (rommapp/romm:5.3.1 + mariadb:11, project argosy-repro), user
#   repro, library = ./library (LeafGreen Rev 1); scanned with
#   docker compose exec -w /backend romm python -c 'import asyncio;from endpoints.sockets.scan import scan_platforms;asyncio.run(scan_platforms([],[],launchbox_remote_enabled=False))'
#   Argosy paired by approving the device code via POST /api/auth/device/approve.
# - Argosy settings: Save Sync on, Secure Saves on, Dual-screen on, Screen Dimmer off, per-game
#   emulator Minarch Link, HOME role = Argosy, usage access + all-files + overlay granted (appops),
#   secure theme_customization_overlay_packages color_source=lock_wallpaper (the default).
# - Never set theme_customization by hand without restoring color_source=lock_wallpaper, or a0 stops
#   reproducing (the lock-art recolor is what relaunches MainActivity).
# - Another TCP adb device (e.g. a real Thor over Wi-Fi) breaks the emulator's own "adb -e" call;
#   `up` starts the guest multidisplay service itself. Everything here uses -s emulator-5580 only.
