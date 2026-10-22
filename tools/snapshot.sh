#!/usr/bin/env bash
# ============================================================
# 创建源码快照（改动前先跑一次，方便回滚）
# 用法: tools/snapshot.sh [标签]
#   例: tools/snapshot.sh before-card-scan
# 产物: snapshots/<标签>-<时间戳>/{src, build.gradle, settings.gradle}
# ============================================================
set -e

PROJ="$(cd "$(dirname "$0")/.." && pwd)"
APP="$PROJ/android-app"
LABEL="${1:-snap}"
TS="$(date +%Y%m%d-%H%M%S)"
DEST="$PROJ/snapshots/$LABEL-$TS"

mkdir -p "$DEST"
cp -r "$APP/app/src" "$DEST/src"
cp "$APP/app/build.gradle" "$DEST/build.gradle"
[ -f "$APP/settings.gradle" ] && cp "$APP/settings.gradle" "$DEST/settings.gradle" || true

echo "$DEST" > "$PROJ/snapshots/LAST_SNAPSHOT"
echo "✅ 快照已创建: $DEST"
du -sh "$DEST"
