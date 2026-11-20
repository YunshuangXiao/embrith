#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# 在编译容器里运行:  bash 03-container.sh run './08-package.sh'
# 1) 检查编译产物里我们关心的东西, 输出到 pkg/check.txt
# 2) 生成单个可拷到 Kindle 的文件: pkg/embrith.raw
#    布局: [0..511] 头部文本 "EMBRITH1 SYS_OFF=.. SYS_LEN=.. ROOT_OFF=.. ROOT_LEN=.." (其余补 NUL)
#          [SYS_OFF..] system.raw (ext4, host 端 losetup -o 挂载)
#          [ROOT_OFF..] root.tar (tar 字节流, 不解开, host 端直接读取)
set -e
cd "$(dirname "$0")"
export HOME="$PWD/.build-home"; mkdir -p "$HOME"
export LC_ALL=C USER=${USER:-$(id -un 2>/dev/null || echo builder)}
P=out/target/product/pw6
PKG=$PWD/pkg; mkdir -p "$PKG"
# 05-apply.sh rewrites device/kindle/pw6/* every time. If system.img is OLDER than that, the build did not finish (or did not run)
# and we would package yesterday's image.
if [ ! -f $P/system.img ] || [ ! $P/system.img -nt device/kindle/pw6/kindle_pw6.mk ]; then
  echo "== STOP: $P/system.img is older than device/kindle/pw6/kindle_pw6.mk."
  echo "   The last build failed or was not run after 05-apply.sh. Run ./kindle-make.sh again and read the end of its output."
  exit 1
fi

{
echo "== 我们的模块"
ls -l $P/system/lib/hw/ 2>&1
echo "-- egl"; ls -l $P/system/lib/egl/ 2>&1
echo "== 关键程序"
for b in servicemanager surfaceflinger app_process app_process32 logcat sh toolbox adbd vold netd installd; do
  ls -l $P/system/bin/$b 2>/dev/null || echo "missing: $b"
done
echo "== build.prop 关键项"
grep -E "ro.kernel.qemu|ro.sf.lcd_density|ro.zygote|ro.build.type|ro.product.cpu.abi|ro.hardware|ro.board.platform|debug.sf" $P/system/build.prop || true
echo "== root 目录"; ls -la $P/root | head -60
echo "== root/default.prop"; cat $P/root/default.prop 2>/dev/null || true
echo "== init.rc import/service"; grep -nE "^import|^service" $P/root/init.rc | head -60
echo "== 大小"; du -sh $P/system $P/root
echo "== 应用"; ls $P/system/app $P/system/priv-app 2>/dev/null
} > "$PKG/check.txt" 2>&1
cat "$PKG/check.txt"

# 中间文件放在临时目录, 打包后删除, pkg/ 下只留一个文件
STAGE=$PKG/.stage; rm -rf "$STAGE"; mkdir -p "$STAGE"

echo "== 生成 system.raw"
SIMG=out/host/linux-x86/bin/simg2img
if [ ! -x "$SIMG" ]; then
  ( set +e; source build/envsetup.sh >/dev/null; lunch kindle_pw6-userdebug >/dev/null; make -j3 simg2img )
fi
$SIMG $P/system.img "$STAGE/system.raw"
e2fsck -fy "$STAGE/system.raw" || true
resize2fs -M "$STAGE/system.raw"
# resize2fs -M 只缩小文件系统, 不缩小镜像文件; 截断到文件系统实际大小, 使 SYS_LEN 精确
BS=$(dumpe2fs -h "$STAGE/system.raw" 2>/dev/null | awk -F: '/^Block size/ {gsub(/ /, "", $2); print $2}')
BC=$(dumpe2fs -h "$STAGE/system.raw" 2>/dev/null | awk -F: '/^Block count/ {gsub(/ /, "", $2); print $2}')
SYS_LEN=$((BC * BS))
truncate -s "$SYS_LEN" "$STAGE/system.raw"

echo "== 生成 root.tar"
tar -C $P/root --owner=0 --group=0 --numeric-owner -cf "$STAGE/root.tar" .
ROOT_LEN=$(stat -c %s "$STAGE/root.tar")

echo "== 生成 embrith.raw"
SYS_OFF=512
ROOT_OFF=$((SYS_OFF + SYS_LEN))
printf 'EMBRITH1 SYS_OFF=%d SYS_LEN=%d ROOT_OFF=%d ROOT_LEN=%d\n' "$SYS_OFF" "$SYS_LEN" "$ROOT_OFF" "$ROOT_LEN" > "$STAGE/head"
truncate -s 512 "$STAGE/head"
rm -f "$PKG/embrith.raw" "$PKG/embrith-image.tar"
cat "$STAGE/head" "$STAGE/system.raw" "$STAGE/root.tar" > "$PKG/embrith.raw"
rm -rf "$STAGE"
( cd "$PKG" && sha256sum embrith.raw && chmod 777 embrith.raw check.txt && ls -lh embrith.raw )