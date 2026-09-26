#!/usr/bin/env bash
# Manual deliver of API + Web to nelsen-server (bypasses GitHub Actions).
#
# Preferred (runs clone+build ON the server — no local rsync of node_modules):
#   bash scripts/manual-deploy.sh
#   bash scripts/manual-deploy.sh api
#   bash scripts/manual-deploy.sh web
#
# HOST defaults to server-remote (see ~/.ssh/config).
set -euo pipefail

HOST="${HOST:-server-remote}"
TARGET="${1:-all}"
SSH_CFG="${SSH_CFG:-$HOME/.ssh/config}"
SSH=(ssh -F "$SSH_CFG" -o BatchMode=yes -o ConnectTimeout=30)

echo "==> Manual deploy on $HOST (target=$TARGET) from origin/main"
"${SSH[@]}" "$HOST" "TARGET='$TARGET' bash -s" <<'REMOTE'
set -euo pipefail
TARGET="${TARGET:-all}"
WORKDIR=/tmp/nelsen-savannah-deploy
APP_DIR=/home/server/Apis/nelsen-savannah
WEB_DIR=/var/www/nelsen-savannah
WEB_MIRROR=/home/server/web-projects/nelsen-savannah
PM2=/usr/local/bin/pm2

echo "==> Clone main"
rm -rf "$WORKDIR"
git clone --depth 1 --branch main https://github.com/tomleejumah/nelsen-savannah.git "$WORKDIR"
cd "$WORKDIR"
echo "HEAD $(git rev-parse --short HEAD)"

if [ "$TARGET" = "all" ] || [ "$TARGET" = "api" ]; then
  echo "==> Deploy API"
  mkdir -p "$APP_DIR" "$APP_DIR/data" "$APP_DIR/uploads" "$APP_DIR/uploads/_tmp"
  rsync -a --delete \
    --exclude node_modules --exclude .env --exclude firebase-service-account.json \
    --exclude .github --exclude uploads/ --exclude data/ --exclude .git \
    "$WORKDIR/api/" "$APP_DIR/"
  cd "$APP_DIR"
  test -f .env
  test -f firebase-service-account.json
  rm -rf node_modules
  npm install --omit=dev --no-audit --no-fund
  "$PM2" delete nelsen-savannah || true
  "$PM2" start src/app.js --name nelsen-savannah --cwd "$APP_DIR" --update-env
  "$PM2" save
  ok=0
  for i in $(seq 1 25); do
    if curl -sf --max-time 2 http://127.0.0.1:5002/lms/health \
      | grep -Eq '"ok"[[:space:]]*:[[:space:]]*true|"status"[[:space:]]*:[[:space:]]*"OK"'; then
      ok=1; echo "API ready attempt $i"; break
    fi
    sleep 1
  done
  test "$ok" = 1
fi

if [ "$TARGET" = "all" ] || [ "$TARGET" = "web" ]; then
  echo "==> Build + deploy Web"
  cd "$WORKDIR/web"
  npm ci
  npm run build
  DIST="$WORKDIR/web/dist/client"
  test -d "$DIST"
  mkdir -p "$WEB_DIR" "$WEB_MIRROR"
  rsync -a --delete --exclude .git "$DIST/" "$WEB_DIR/"
  rsync -a --delete --exclude .git "$DIST/" "$WEB_MIRROR/"
  find "$WEB_DIR" "$WEB_MIRROR" -type d -exec chmod 755 {} +
  find "$WEB_DIR" "$WEB_MIRROR" -type f -exec chmod 644 {} +
fi

echo "==> Smoke"
curl -sf http://127.0.0.1:5002/lms/health | head -c 180; echo
curl -sf -o /dev/null -w "home %{http_code}\n" -H "Host: nelsen-savannah.co.ke" http://127.0.0.1/
curl -sf -o /dev/null -w "pdf.worker.mjs %{http_code}\n" -H "Host: nelsen-savannah.co.ke" http://127.0.0.1/pdf.worker.min.mjs || true
echo "==> DONE $(git -C "$WORKDIR" rev-parse --short HEAD)"
REMOTE
