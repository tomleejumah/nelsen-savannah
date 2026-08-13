#!/usr/bin/env bash
#
# server-cleanup.sh — reclaim disk on the Nelsen Savannah deployment host.
#
# Runs DRY-RUN by default: prints every candidate and the bytes it would
# reclaim, and changes nothing. Pass --apply to actually delete.
#
#   ./server-cleanup.sh            # report only
#   ./server-cleanup.sh --apply    # delete
#
# What it touches:
#   - PM2 logs (truncate, then install pm2-logrotate so they stop growing)
#   - PM2 stale dump/backup files
#   - npm cache (prune via `npm cache verify`)
#   - GitHub Actions self-hosted runner _work/_temp and _diag leftovers
#   - Stale runner checkouts for repos that are no longer deployed here
#
# What it deliberately NEVER touches — the app's live state lives here:
#   - /home/server/WebHooks/Nisisi-Africa/uploads/   (media, incl. _tmp)
#   - /home/server/WebHooks/Nisisi-Africa/data/      (SQLite database)
#   - .env, firebase-service-account.json
#   - node_modules of the running app (deploy rebuilds it; deleting it
#     mid-flight takes the API down)
#
set -euo pipefail

# Path keeps the legacy directory name — the live app still deploys there.
APP_DIR="${NELSEN_APP_DIR:-/home/server/WebHooks/Nisisi-Africa}"
PM2_HOME="${PM2_HOME:-$HOME/.pm2}"
NPM_CACHE="${NPM_CONFIG_CACHE:-$HOME/.npm}"

# The runner root is auto-detected: the dir containing both `svc.sh` and `_work`.
RUNNER_SEARCH_PATHS=(
  "$HOME/actions-runner"
  "/opt/actions-runner"
  "/home/server/actions-runner"
  "/home/server/WebHooks/actions-runner"
)

# Repo checkouts under _work that are still deployed from this host. Anything
# else under _work is a leftover from a repo that moved or was renamed.
ACTIVE_RUNNER_REPOS=("nelsen-savannah")

APPLY=0
case "${1:-}" in
  --apply) APPLY=1 ;;
  ""|--dry-run) APPLY=0 ;;
  *) echo "usage: $0 [--apply|--dry-run]" >&2; exit 2 ;;
esac

TOTAL=0
FOUND_ANYTHING=0

log()  { printf '%s\n' "$*"; }
head2() { printf '\n=== %s ===\n' "$*"; }

# Bytes of a path, 0 if missing.
size_of() {
  [ -e "$1" ] || { echo 0; return; }
  du -sb -- "$1" 2>/dev/null | cut -f1
}

human() { numfmt --to=iec --suffix=B "${1:-0}" 2>/dev/null || echo "${1:-0} bytes"; }

# Record a deletion candidate and, with --apply, remove it.
# Refuses anything that resolves inside the protected app state dirs.
consider() {
  local path="$1" what="$2" bytes
  [ -e "$path" ] || return 0

  case "$path" in
    "$APP_DIR"/uploads*|"$APP_DIR"/data*|*/.env|*firebase-service-account.json)
      log "REFUSING (protected app state): $path"
      return 0
      ;;
  esac

  bytes=$(size_of "$path")
  TOTAL=$((TOTAL + bytes))
  FOUND_ANYTHING=1

  if [ "$APPLY" -eq 1 ]; then
    log "  removing  $(human "$bytes")  $path   ($what)"
    rm -rf -- "$path"
  else
    log "  would remove  $(human "$bytes")  $path   ($what)"
  fi
}

# ---------------------------------------------------------------------------
# Preflight: refuse to run if this doesn't look like the deployment host.
# ---------------------------------------------------------------------------
if [ ! -d "$APP_DIR" ]; then
  echo "ERROR: $APP_DIR not found — this does not look like the Nelsen Savannah" >&2
  echo "       deployment host. Refusing to run." >&2
  exit 1
fi
if [ ! -d "$PM2_HOME" ]; then
  echo "ERROR: PM2 home $PM2_HOME not found. Refusing to run." >&2
  echo "       If PM2 runs as another user, re-run as that user or set PM2_HOME." >&2
  exit 1
fi

if [ "$APPLY" -eq 1 ]; then
  log "MODE: APPLY — files will be deleted."
else
  log "MODE: DRY-RUN — nothing will be deleted. Re-run with --apply to delete."
fi
log "app dir:   $APP_DIR"
log "pm2 home:  $PM2_HOME"
log "npm cache: $NPM_CACHE"

head2 "Disk before"
df -h "$APP_DIR" || true

# ---------------------------------------------------------------------------
# 1. PM2 logs — unbounded without logrotate; the usual silent disk eater.
#    Truncated in place (not deleted) so PM2's open file handles stay valid.
# ---------------------------------------------------------------------------
head2 "PM2 logs ($PM2_HOME/logs)"
if [ -d "$PM2_HOME/logs" ]; then
  pm2_log_bytes=$(size_of "$PM2_HOME/logs")
  if [ "$pm2_log_bytes" -gt 0 ]; then
    find "$PM2_HOME/logs" -maxdepth 1 -type f -name '*.log' -size +1M \
      -printf '  %10s bytes  %p\n' 2>/dev/null || true
    log "  total in logs dir: $(human "$pm2_log_bytes")"
    TOTAL=$((TOTAL + pm2_log_bytes))
    FOUND_ANYTHING=1
    if [ "$APPLY" -eq 1 ]; then
      # Truncate rather than rm: PM2 holds these open, a deleted inode would
      # keep consuming space until restart.
      while IFS= read -r -d '' f; do
        : > "$f"
        log "  truncated $f"
      done < <(find "$PM2_HOME/logs" -maxdepth 1 -type f -name '*.log' -print0)
      # Rotated/compressed leftovers are safe to delete outright.
      find "$PM2_HOME/logs" -maxdepth 1 -type f \
        \( -name '*.log.gz' -o -name '*__*.log' \) -delete
    else
      log "  would truncate all *.log here and delete rotated *.log.gz"
    fi
  else
    log "  nothing to reclaim"
  fi
else
  log "  no logs dir"
fi

# ---------------------------------------------------------------------------
# 2. Stop the growth: pm2-logrotate. Without this, step 1 is a treadmill.
# ---------------------------------------------------------------------------
head2 "PM2 log rotation"
PM2_BIN="$(command -v pm2 || echo /usr/local/bin/pm2)"
if [ -x "$PM2_BIN" ]; then
  if "$PM2_BIN" ls 2>/dev/null | grep -q 'pm2-logrotate'; then
    log "  pm2-logrotate already installed"
  elif [ "$APPLY" -eq 1 ]; then
    log "  installing pm2-logrotate (10M max, keep 7, compress)"
    "$PM2_BIN" install pm2-logrotate
    "$PM2_BIN" set pm2-logrotate:max_size 10M
    "$PM2_BIN" set pm2-logrotate:retain 7
    "$PM2_BIN" set pm2-logrotate:compress true
  else
    log "  would install pm2-logrotate (max_size 10M, retain 7, compress true)"
    log "  NOTE: this is the actual fix — truncating logs without it just defers the problem."
  fi
else
  log "  pm2 binary not found at $PM2_BIN — skipping rotation setup"
fi

# ---------------------------------------------------------------------------
# 3. PM2 dump backups — process-list snapshots, regenerated by `pm2 save`.
#    The live dump.pm2 is kept; only .bak copies go.
# ---------------------------------------------------------------------------
head2 "PM2 stale dump files"
if [ -d "$PM2_HOME" ]; then
  while IFS= read -r -d '' f; do
    consider "$f" "pm2 process-list backup, regenerated by 'pm2 save'"
  done < <(find "$PM2_HOME" -maxdepth 1 -type f -name 'dump.pm2.bak*' -print0 2>/dev/null)
fi
[ -f "$PM2_HOME/dump.pm2" ] && log "  keeping live $PM2_HOME/dump.pm2"

# ---------------------------------------------------------------------------
# 4. npm cache — the deploy runs `rm -rf node_modules && npm install` on every
#    push, so this grows with every dependency version ever installed.
#    `npm cache verify` prunes unreferenced/expired entries (safe; a cache miss
#    only costs a re-download).
# ---------------------------------------------------------------------------
head2 "npm cache ($NPM_CACHE/_cacache)"
if [ -d "$NPM_CACHE/_cacache" ]; then
  npm_bytes=$(size_of "$NPM_CACHE/_cacache")
  log "  current size: $(human "$npm_bytes")"
  FOUND_ANYTHING=1
  if [ "$APPLY" -eq 1 ]; then
    npm cache verify || log "  npm cache verify returned nonzero — continuing"
    log "  size after prune: $(human "$(size_of "$NPM_CACHE/_cacache")")"
  else
    log "  would run 'npm cache verify' to prune expired/unreferenced entries"
    log "  (for a full reclaim of the figure above: npm cache clean --force)"
  fi
  # npm debug logs accumulate one file per failed install, forever.
  if [ -d "$NPM_CACHE/_logs" ]; then
    consider "$NPM_CACHE/_logs" "npm debug logs (regenerated as needed)"
  fi
else
  log "  no npm cache found"
fi

# ---------------------------------------------------------------------------
# 5. Self-hosted runner _work — normally the biggest hog on the box. The runner
#    keeps a full checkout per repo plus _temp/_diag, and does NOT prune these.
# ---------------------------------------------------------------------------
head2 "GitHub Actions runner work dir"
RUNNER_ROOT=""
for candidate in "${RUNNER_SEARCH_PATHS[@]}"; do
  if [ -d "$candidate/_work" ]; then RUNNER_ROOT="$candidate"; break; fi
done
# Fall back to a shallow search so an unexpected install path still gets found.
if [ -z "$RUNNER_ROOT" ]; then
  RUNNER_ROOT="$(find /home /opt -maxdepth 3 -type d -name '_work' 2>/dev/null \
    | head -1 | xargs -r dirname || true)"
fi

if [ -z "$RUNNER_ROOT" ] || [ ! -d "$RUNNER_ROOT/_work" ]; then
  log "  runner _work dir not found in any known location."
  log "  Searched: ${RUNNER_SEARCH_PATHS[*]}"
  log "  If the runner lives elsewhere, add its path to RUNNER_SEARCH_PATHS above."
else
  log "  runner root: $RUNNER_ROOT"
  log "  _work total: $(human "$(size_of "$RUNNER_ROOT/_work")")"

  # A job in flight will be writing here — never clean mid-run.
  if pgrep -f 'Runner.Worker' >/dev/null 2>&1; then
    log "  !! A workflow job is RUNNING right now (Runner.Worker alive)."
    log "  !! Skipping runner cleanup. Re-run when idle."
  else
    # _temp and _diag are pure scratch/logs; the runner recreates them.
    consider "$RUNNER_ROOT/_work/_temp" "runner scratch dir, recreated each job"
    consider "$RUNNER_ROOT/_work/_actions" "cached action tarballs, re-downloaded on next run"
    consider "$RUNNER_ROOT/_diag" "runner diagnostic logs"
    if [ -d "$RUNNER_ROOT/_work/_diag" ]; then
      consider "$RUNNER_ROOT/_work/_diag" "per-job diagnostic logs"
    fi

    # Stale per-repo checkouts: anything not in ACTIVE_RUNNER_REPOS.
    log "  repo checkouts under _work:"
    for d in "$RUNNER_ROOT/_work"/*; do
      [ -d "$d" ] || continue
      name="$(basename "$d")"
      case "$name" in
        _temp|_actions|_tool|_diag|_temp_*) continue ;;
      esac
      is_active=0
      for active in "${ACTIVE_RUNNER_REPOS[@]}"; do
        [ "$name" = "$active" ] && is_active=1
      done
      if [ "$is_active" -eq 1 ]; then
        log "    keeping (active)  $(human "$(size_of "$d")")  $name"
        # Even for the active repo, the checkout is disposable — actions/checkout
        # re-clones. But keeping it makes the next deploy much faster, so we only
        # clear build leftovers inside it, not the checkout itself.
      else
        consider "$d" "stale checkout — repo not in ACTIVE_RUNNER_REPOS"
      fi
    done
  fi
fi

# ---------------------------------------------------------------------------
# 6. Report
# ---------------------------------------------------------------------------
head2 "Summary"
if [ "$FOUND_ANYTHING" -eq 0 ]; then
  log "Nothing to reclaim — the host is already clean."
else
  if [ "$APPLY" -eq 1 ]; then
    log "Reclaimed approximately: $(human "$TOTAL")"
  else
    log "Reclaimable (estimate):  $(human "$TOTAL")"
    log ""
    log "Nothing was deleted. To apply:  $0 --apply"
  fi
fi

head2 "Disk after"
df -h "$APP_DIR" || true

# ---------------------------------------------------------------------------
# NOT handled here, on purpose
# ---------------------------------------------------------------------------
cat <<'NOTE'

Out of scope for this script
---------------------------
* uploads/_tmp orphaned partial uploads
    Files land in uploads/_tmp when an upload starts but never finalizes, so
    they accumulate with no owning DB row. Reaping them needs the media
    metadata table to tell an orphan from an in-flight upload — deleting by
    mtime alone would race a slow upload. That reaper belongs to the
    media-storage pipeline work, not to a disk-cleanup script. This script
    never touches uploads/ at all.

* The app's node_modules
    The deploy already does `rm -rf node_modules && npm install --production`
    on every push, so there is nothing stale to collect, and removing it
    outside a deploy would stop the API.
NOTE
