#!/usr/bin/env bash
# ============================================================
# 代码回滚：把源码恢复到某个快照
# 用法:
#   tools/rollback.sh                 # 回滚到最近一次快照
#   tools/rollback.sh <快照目录>       # 回滚到指定快照
#   tools/rollback.sh baseline        # 回滚到最早那份 baseline-*（正式 1.2.0 基线）
#
# 安全措施：回滚前会先自动给「当前状态」拍一张快照（标签 auto-before-rollback），
#          所以回滚本身也可以再退回来。
# 注意：只覆盖 android-app/app/src 与 build.gradle，不动 android-sdk / gradle / apk。
# ============================================================
set -e

PROJ="$(cd "$(dirname "$0")/.." && pwd)"
APP="$PROJ/android-app"
SNAPS="$PROJ/snapshots"

ARG="${1:-}"

if [ -z "$ARG" ]; then
  [ -f "$SNAPS/LAST_SNAPSHOT" ] || { echo "❌ 没有任何快照"; exit 1; }
  TARGET="$(cat "$SNAPS/LAST_SNAPSHOT")"
elif [ "$ARG" = "baseline" ]; then
  TARGET="$(ls -d "$SNAPS"/baseline-* 2>/dev/null | head -1)"
  [ -n "$TARGET" ] || { echo "❌ 找不到 baseline-* 快照"; exit 1; }
elif [ -d "$ARG" ]; then
  TARGET="$ARG"
elif [ -d "$SNAPS/$ARG" ]; then
  TARGET="$SNAPS/$ARG"
else
  echo "❌ 快照不存在: $ARG"
  echo "可用快照:"
  ls -1 "$SNAPS" | grep -v '^LAST_' || true
  exit 1
fi

[ -d "$TARGET/src" ] || { echo "❌ 快照不完整（缺 src）: $TARGET"; exit 1; }

# 1) 先把当前状态备份（可反悔）
TS="$(date +%Y%m%d-%H%M%S)"
SAFE="$SNAPS/auto-before-rollback-$TS"
mkdir -p "$SAFE"
cp -r "$APP/app/src" "$SAFE/src"
cp "$APP/app/build.gradle" "$SAFE/build.gradle"
echo "🛟 当前状态已备份: $SAFE"

# 2) 覆盖源码
rm -rf "$APP/app/src"
cp -r "$TARGET/src" "$APP/app/src"
cp "$TARGET/build.gradle" "$APP/app/build.gradle"

echo "✅ 已回滚到: $TARGET"
echo "   如需反悔: tools/rollback.sh $SAFE"
