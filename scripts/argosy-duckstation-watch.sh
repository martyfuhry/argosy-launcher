#!/system/bin/sh
# Keep DuckStation's memory cards and save states readable by Argosy.
#
# Run this through your device's "run script as root" setting, after
# argosy-android-data.sh. Do NOT run it over adb.
#
# DuckStation for Android is frozen upstream and creates its save files with
# mode 600, so nothing but the app itself can read them. Folder permissions do
# not help, because the block is on the files. This starts a watcher that gives
# each new save file group read as soon as DuckStation closes it.
#
# The watcher does not survive a reboot on a device with no root-manager module,
# so re-run it after restarting. On Magisk, KernelSU or APatch, copy the printed
# command into that manager's boot service instead and it will start itself.
#
# Every run appends to /data/local/tmp/argosy-duckstation-watch.log.

PKG="${1:-com.nendo.argosy}"
DUCK="com.github.stenzek.duckstation"
ROOT="/data/media/0"
HANDLER="/data/local/tmp/argosy-fixperm.sh"
PIDFILE="/data/local/tmp/argosy-duckstation-watch.pid"
LOG="/data/local/tmp/argosy-duckstation-watch.log"

echo "" >> "$LOG" 2>/dev/null
chmod 666 "$LOG" 2>/dev/null
exec 3>>"$LOG"

log() {
    echo "[duckstation-watch] $1"
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >&3
}

fail() {
    log "FAILED: $1"
    log "--- run ended ---"
    exit 1
}

log "--- run started ---"

[ "$(id -u)" = "0" ] || fail "not running as root. Use the device's 'run script as root' option."
command -v inotifyd >/dev/null 2>&1 || fail "this device has no inotifyd, so the watcher cannot run."

APP_UID=$(dumpsys package "$PKG" 2>/dev/null | grep -m1 "appId=" | sed 's/.*appId=\([0-9]*\).*/\1/')
case "$APP_UID" in
    ''|*[!0-9]*)
        APP_UID=$(dumpsys package "$PKG" 2>/dev/null | grep -m1 "userId=" | sed 's/.*userId=\([0-9]*\).*/\1/')
        ;;
esac
case "$APP_UID" in
    ''|*[!0-9]*) fail "could not read the uid of $PKG. Is Argosy installed?" ;;
esac
log "$PKG runs as uid $APP_UID"

BASE="$ROOT/Android/data/$DUCK/files"
[ -d "$BASE" ] || fail "$BASE does not exist. Is DuckStation installed and has it been run once?"

if [ -f "$PIDFILE" ]; then
    OLD=$(cat "$PIDFILE" 2>/dev/null)
    if [ -n "$OLD" ] && kill -0 "$OLD" 2>/dev/null; then
        log "stopping the previous watcher (pid $OLD)"
        kill "$OLD" 2>/dev/null
    fi
    rm -f "$PIDFILE"
fi

cat > "$HANDLER" <<EOF
#!/system/bin/sh
target="\$2"
[ -n "\$3" ] && target="\$2/\$3"
[ -f "\$target" ] || exit 0
chgrp $APP_UID "\$target" 2>/dev/null
chmod 660 "\$target" 2>/dev/null
EOF
chmod 755 "$HANDLER" || fail "could not write the handler at $HANDLER"

WATCHED=""
for sub in memcards savestates; do
    dir="$BASE/$sub"
    if [ -d "$dir" ]; then
        chgrp -R "$APP_UID" "$dir" 2>/dev/null
        chmod 2770 "$dir" 2>/dev/null
        find "$dir" -type f -exec chmod 660 {} \; 2>/dev/null
        WATCHED="$WATCHED $dir:nw"
        log "watching $dir and fixed the files already there"
    else
        log "skipping $sub, it does not exist yet"
    fi
done

[ -n "$WATCHED" ] || fail "neither memcards nor savestates exists yet. Run a game once, then re-run."

# shellcheck disable=SC2086
setsid nohup inotifyd "$HANDLER" $WATCHED >/dev/null 2>&1 &
WATCH_PID=$!
echo "$WATCH_PID" > "$PIDFILE"
chmod 666 "$PIDFILE" 2>/dev/null

sleep 2
if kill -0 "$WATCH_PID" 2>/dev/null; then
    log "OK: watcher running as pid $WATCH_PID"
    log "to start it automatically, put this in your root manager's boot service:"
    log "  sh /sdcard/argosy-duckstation-watch.sh"
else
    fail "the watcher exited immediately; this device may kill background root processes."
fi

log "--- run ended ---"
