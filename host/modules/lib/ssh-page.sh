#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Embrith contributors
# embrith SSH page (no Android needed). Drawn like a normal page with fbink (OpenType text, filled boxes) and operated by touch:
# one card per source (Embrith, KOReader) with a switch, a port stepper and the ssh command. The Kindle UI keeps running: this is a
# library scriptlet, the page is drawn over it and the library is opened again on exit (same way as the Yunindex reading-time page).
# Tap the switch = on/off, tap < or > = previous/next port, tap the cross (top right) = leave. 5 minutes without a touch = leave.
# Fonts: a Chinese OpenType font gives Chinese labels (looked for in the Kindle fonts folder /mnt/us/fonts, reading-time/fonts, then the system); else English labels.
PATH=/sbin:/bin:/usr/sbin:/usr/bin:/usr/local/bin
export PATH

D=${EMBRITH_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}   # program folder (modules, bin)
S=${EMBRITH_USER:-/mnt/us/embrith}                         # user folder (logs, ssh-page.conf)
TOGGLE=$D/modules/lib/ssh-toggle.sh
# 屏幕尺寸启动时从帧缓冲读取 (kutil panel), 没有内置默认值; 版面各数值 (px) 仍按 1272x1696 设计, 换分辨率后需要重排 (未验证)
PANEL=$("$D/bin/kutil" panel 2>/dev/null); LOGICAL_W=${PANEL% *}; LOGICAL_H=${PANEL#* }
case "$LOGICAL_W$LOGICAL_H" in ""|*[!0-9]*) exit 1 ;; esac
FLASH_EVERY=5; RENDER_N=0
FS=143     # font scale (percent): fbink -t draws px=N about 0.7 times smaller than N on this Kindle, so every size below is multiplied by FS/100; lower it in ssh-page.conf if the text is too big
X_OFF=0    # shift the whole page sideways (px, negative = left); put e.g. X_OFF=24 in $S/ssh-page.conf if the page looks off-centre
[ -r "$S/ssh-page.conf" ] && . "$S/ssh-page.conf"
FBINK_LOG=$S/logs/ssh-page-fbink.log

FBINK=${EMBRITH_FBINK:-}
if [ -z "$FBINK" ]; then
  for c in /mnt/us/libkh/bin/fbink /var/local/kmc/bin/fbink /mnt/us/koreader/fbink; do [ -x "$c" ] && { FBINK=$c; break; }; done
fi
[ -x "$FBINK" ] && [ -r "$TOGGLE" ] || exit 1
mkdir -p "$S/logs" 2>/dev/null

# ---- touch device (same search as the reading-time page) ----
find_touch_device() {
  for pat in pt_mt touch zforce cyttsp fts goodix capmulti elan _ts; do
    for ep in /sys/class/input/event*; do
      [ -r "$ep/device/name" ] || continue
      nm=$(tr '[:upper:]' '[:lower:]' < "$ep/device/name" 2>/dev/null)
      case "$nm" in
        *pwrkey*|*power*|*-key*|*keypad*|*button*) continue ;;
        *"$pat"*) c=/dev/input/${ep##*/}; [ -r "$c" ] && { TOUCH=$c; return; } ;;
      esac
    done
  done
  TOUCH=/dev/input/event1
}
TOUCH=${EMBRITH_TOUCH:-}
[ -n "$TOUCH" ] || find_touch_device

# ---- fonts: a Chinese one gives Chinese labels ----
RFONT=""; BFONT=""; ZH=0
for r in "/mnt/us/fonts/NotoSerifSC-Regular.otf" "/mnt/us/fonts/NotoSansSC-Regular.otf" "/mnt/us/reading-time/fonts/NotoSerifSC-Regular.otf" "/mnt/us/reading-time/NotoSerifSC-Regular.otf"; do
  [ -f "$r" ] && { RFONT=$r; ZH=1; break; }
done
if [ -n "$RFONT" ]; then
  BFONT=${RFONT%Regular.otf}Bold.otf
  [ -f "$BFONT" ] || BFONT=$RFONT
else
  for r in /usr/java/lib/fonts/Caecilia_LT_65_Medium.ttf /usr/java/lib/fonts/*Ember*Rg*.ttf /usr/java/lib/fonts/*.ttf; do
    [ -f "$r" ] && { RFONT=$r; break; }
  done
  BFONT=$RFONT
  for r in /usr/java/lib/fonts/Caecilia_LT_75_Bold.ttf; do [ -f "$r" ] && BFONT=$r; done
fi
[ -n "$RFONT" ] || exit 1

if [ "$ZH" = 1 ]; then
  T_TITLE="SSH 远程登录"; T_ON="开"; T_OFF="关"; T_PORT="端口"; T_NOINST="未安装"; T_WAIT="..."
  T_NOWIFI="Wi-Fi 未连接"; T_WIFI="Wi-Fi 地址"; T_OFFTXT="未开启"; T_LOGIN="登录方式"
  T_PW="系统密码"; T_PWC="自定义密码"; T_KEY="仅密钥"; T_NONE="免密"
  T_EMB="Embrith"; T_KO="KOReader"; T_EMBSUB="内置 dropbear"; T_KOSUB="内置 dropbear"
  T_DEL="×"; T_OK="✓"; T_CANCEL="取消"; T_RANGE="端口范围 1-65535"; T_BAD="端口不对, 范围 1-65535"; T_DUP="这个端口已被另一个来源使用"
  T_KN1="注意：请避免使用常见端口，如 80、443、8080、8888、5555 等，"
  T_KN2="以免影响系统功能。建议使用 1024 以上不常用的端口，"
  T_KN3="例如 2222、2022、8022。"
  T_NOTE1="点开关启停，点端口输入新端口，点登录方式切换。"
  T_NOTE2="Embrith 的自定义密码在安卓设置里设; KOReader 没有自定义密码。"
  T_NOTE3="免密登录时同一网络里任何人都能连，只建议临时使用。"
else
  T_TITLE="SSH"; T_ON="ON"; T_OFF="OFF"; T_PORT="Port"; T_NOINST="not installed"; T_WAIT="..."
  T_NOWIFI="Wi-Fi not connected"; T_WIFI="Wi-Fi address"; T_OFFTXT="off"; T_LOGIN="Login"
  T_PW="System password"; T_PWC="Custom password"; T_KEY="Key only"; T_NONE="No password"
  T_EMB="Embrith"; T_KO="KOReader"; T_EMBSUB="built-in dropbear"; T_KOSUB="KOReader's dropbear"
  T_DEL="×"; T_OK="✓"; T_CANCEL="Cancel"; T_RANGE="Port 1-65535"; T_BAD="Bad port, use 1-65535"; T_DUP="Port used by the other source"
  T_KN1="Avoid common ports such as 80, 443, 8080, 8888 or 5555,"
  T_KN2="they can break system features. Prefer an uncommon port"
  T_KN3="above 1024, for example 2222, 2022 or 8022."
  T_NOTE1="Tap the switch to start or stop, the port to change it, Login to switch."
  T_NOTE2="Set a custom Embrith password in the Android settings."
  T_NOTE3="No password: anyone on the network can log in. Temporary use only."
fi

# ---- drawing helpers ----
# text with its left edge at $3 and its right limit at $4 (px, top, left, right, style BOLD|REGULAR, color, text); color: BLACK WHITE GRAY1..GRAYE
fb_text_at() {
  px=$(($1 * FS / 100)); top=$2; left=$3; right=$4; style=$5; fg=$6; msg=$7
  case "$fg" in BLACK|WHITE|GRAY[1-9A-E]) ;; *) fg=BLACK ;; esac
  case "$style" in
    BOLD) fonts="regular=$RFONT,bold=$BFONT"; sty=",style=BOLD" ;;
    *)    fonts="regular=$RFONT"; sty="" ;;
  esac
  l=$((left + X_OFF)); r=$((LOGICAL_W - right - X_OFF)); [ "$l" -lt 0 ] && l=0; [ "$r" -lt 0 ] && r=0
  "$FBINK" -q -b -C "$fg" -O -t "$fonts,px=$px,top=$top,left=$l,right=$r$sty" "$msg" 2>>"$FBINK_LOG"
}

# centered inside [bl, br] by fbink itself (-m centers the text between the left and right margins)
fb_text_center() {   # px top bl br style color text
  px=$(($1 * FS / 100)); top=$2; bl=$3; br=$4; style=$5; fg=$6; msg=$7
  case "$fg" in BLACK|WHITE|GRAY[1-9A-E]) ;; *) fg=BLACK ;; esac
  case "$style" in
    BOLD) fonts="regular=$RFONT,bold=$BFONT"; sty=",style=BOLD" ;;
    *)    fonts="regular=$RFONT"; sty="" ;;
  esac
  l=$((bl + X_OFF)); r=$((LOGICAL_W - br - X_OFF)); [ "$l" -lt 0 ] && l=0; [ "$r" -lt 0 ] && r=0
  "$FBINK" -q -b -m -C "$fg" -O -t "$fonts,px=$px,top=$top,left=$l,right=$r$sty" "$msg" 2>>"$FBINK_LOG"
}

fb_rect() { "$FBINK" -q -b -B "$5" -k "top=$2,left=$(($1 + X_OFF)),width=$3,height=$4" 2>>"$FBINK_LOG"; }   # x y w h color
fb_box() { fb_rect "$1" "$2" "$3" "$4" BLACK; fb_rect $(($1 + 3)) $(($2 + 3)) $(($3 - 6)) $(($4 - 6)) WHITE; }   # 3 px frame

commit_screen() {
  RENDER_N=$((RENDER_N + 1))
  if [ $((RENDER_N % FLASH_EVERY)) -eq 1 ]; then "$FBINK" -q -f -W GC16 -s 2>>"$FBINK_LOG"; else "$FBINK" -q -W GC16 -s 2>>"$FBINK_LOG"; fi
}

# ---- state ----
load_state() {
  E_RDY=0; E_RUN=0; E_PORT=2222; E_MODE=password; E_HF=0; K_RDY=0; K_RUN=0; K_PORT=2222; K_MODE=password; K_HF=0
  L=$(sh "$TOGGLE" list </dev/null 2>/dev/null)
  while read -r n r u p m hf; do
    case "$n" in
      embrith) E_RDY=$r; E_RUN=$u; E_PORT=$p; E_MODE=${m:-password}; E_HF=${hf:-0} ;;
      koreader) K_RDY=$r; K_RUN=$u; K_PORT=$p; K_MODE=${m:-password}; K_HF=0 ;;
    esac
  done <<EOF
$L
EOF
  IP=$(ifconfig wlan0 2>/dev/null | sed -n 's/.*inet addr:\([0-9.]*\).*/\1/p' | head -n 1)
}
mode_label() {   # mode, custom-password flag
  case "$1" in
    key) echo "$T_KEY" ;;
    none) echo "$T_NONE" ;;
    *) if [ "$2" = 1 ]; then echo "$T_PWC"; else echo "$T_PW"; fi ;;
  esac
}

# ---- layout (px, logical 1272x1696) ----
# host/tools/make-ssh-ui.py reads the numbers of this block to draw img/ssh_page_bg.png and img/ssh_keypad_bg.png: keep them in step
FR_X=36; FR_Y=36; FR_W=1200; FR_H=1624      # light outer frame
IN_L=72; IN_R=1200                          # content left and right edge (the dividers are a little shorter than the frame)
LOGO_W=560; LOGO_H=98; LOGO_Y=128           # ssh_logo.png is LOGO_H high, drawn unscaled (LOGO_W = the widest it should be); logo and close button share the centre line y=177
CL_X=1120; CL_Y=145; CL_S=64                # small round close button
Y_D1=278; Y_D2=442; Y_D3=929; Y_D4=1416     # divider lines: under the logo, the title, Embrith and KOReader
Y_T=333                                     # top of the title text
Y_E=497; Y_K=984                            # block tops: Embrith, KOReader
ROW_H=108; ROW_P=108; ROW_M=216; ROW_C=344  # block top + this: the port row, the login row, the ssh command (the first row, name and switch, starts at 0)
VAL_L=560; VAL_GAP=28; CHV_X=1150           # the value is right-aligned (VAL_L = left edge of its clear area), CHV_X = x of the ">"
RAD=24; FR_RAD=40                           # corner radius of the frame and of the keypad boxes (only drawn by the background images)
TITLE_PX=44; LABEL_PX=32; VAL_PX=34; SUB_PX=28; CMD_PX=32; INFO_PX=30   # font sizes (px)
NOTE_PX=${NOTE_PX:-22}; NOTE_Y=1471; NOTE_GAP=40   # notes at the bottom; NOTE_PX can be set in ssh-page.conf
KP_X=162; KP_W=300; KP_H=120; KP_GX=24; KP_GY=20; KP_FW=948   # keypad: first key, key size, gaps, full width
KP_T=289; KP_I=396; KP_IH=120; KP_M=530; KP_Y=596; KP_CY=1166; KP_CH=100   # keypad: title, input box (top, height), message, first key row, cancel button
KP_NY=1306; KP_NGAP=36; KP_NPX=28; KEY_PX=52   # keypad notes (first line, line gap, size), key label size

# images under $D/modules/img: ssh_logo.png is the logo; ssh_page_bg.png and ssh_keypad_bg.png carry the rounded
# frame, the dividers, the close button and the keypad boxes. Without the backgrounds square lines and boxes are drawn instead.
LOGO=""
[ -f "$D/modules/img/ssh_logo.png" ] && LOGO=$D/modules/img/ssh_logo.png
BG_PAGE=""; [ -f "$D/modules/img/ssh_page_bg.png" ] && BG_PAGE=$D/modules/img/ssh_page_bg.png
BG_PAD=""; [ -f "$D/modules/img/ssh_keypad_bg.png" ] && BG_PAD=$D/modules/img/ssh_keypad_bg.png
echo "images: logo=${LOGO:-MISSING} page_bg=${BG_PAGE:-MISSING} keypad_bg=${BG_PAD:-MISSING} (looked in $D/modules/img)" >> "$S/logs/ssh-page.log" 2>/dev/null

fb_image() {   # file x y w h (w and h 0 = native size)
  if [ "$4" = 0 ]; then g="file=$1,x=$(($2 + X_OFF)),y=$3"; else g="file=$1,x=$(($2 + X_OFF)),y=$3,w=$4,h=$5"; fi
  "$FBINK" -q -b -g "$g" 2>>"$FBINK_LOG"
}
fb_hline() { fb_rect $IN_L "$1" $((IN_R - IN_L)) 3 GRAYA; }
draw_frame() {
  fb_rect $FR_X $FR_Y $FR_W 4 GRAYB; fb_rect $FR_X $((FR_Y + FR_H - 4)) $FR_W 4 GRAYB
  fb_rect $FR_X $FR_Y 4 $FR_H GRAYB; fb_rect $((FR_X + FR_W - 4)) $FR_Y 4 $FR_H GRAYB
}
fb_button() { [ -n "$BG_USED" ] || fb_box "$1" "$2" "$3" "$4"; }   # square box, only when there is no background image
# text centred in a button: px y h x0 x1 style color text
fb_btn_text() { fb_text_center "$1" $(($2 + ($3 - $1 * 13 / 10) / 2)) "$4" "$5" "$6" "$7" "$8"; }
refresh_part() { "$FBINK" -q -W DU -s "top=$2,left=$(($1 + X_OFF)),width=$3,height=$4" 2>>"$FBINK_LOG"; }   # x y w h

# a settings row: label on the left, value and ">" on the right; the whole row is the touch area
# advance widths (1/1000 em) of ASCII 32..126 in the page font (measured with the font file); a CJK char is 1000
WT_R="256 308 376 574 550 923 779 196 364 364 478 580 327 346 327 352 557 471 558 557 555 558 558 549 558 562 327 327 580 580 580 432 912 718 670 691 770 652 629 741 853 405 403 731 625 975 796 767 641 767 715 567 659 797 714 1053 700 687 605 344 352 344 580 563 435 557 638 538 629 547 388 566 663 332 311 607 334 974 661 596 638 607 462 473 367 653 548 838 564 552 498 374 316 374 580"
WT_B="250 355 447 615 589 966 803 235 400 400 485 603 335 370 335 400 593 466 594 593 593 594 594 588 594 596 335 335 603 603 603 473 970 746 708 702 789 666 648 757 864 426 428 767 642 1001 815 785 673 785 751 597 695 818 734 1091 726 701 627 372 400 372 603 593 447 585 672 565 658 572 402 596 687 349 335 651 355 1008 685 623 671 640 500 499 393 678 578 874 599 582 517 380 338 380 603"
text_w() {   # px text [B]: width of the text in px (the real glyph widths, so right-aligned text lines up)
  tw_px=$1; tw_st=$3; tw_all=$(printf %s "$2" | wc -c); tw_asc=$(printf %s "$2" | LC_ALL=C sed 's/[^ -~]//g')
  tw_n=${#tw_asc}; tw_sum=0
  if [ "$tw_st" = B ]; then tw_t=$WT_B; else tw_t=$WT_R; fi
  while [ -n "$tw_asc" ]; do
    tw_r=${tw_asc#?}; tw_c=${tw_asc%"$tw_r"}; tw_asc=$tw_r
    tw_o=$(printf %d "'$tw_c"); set -- $tw_t; shift $((tw_o - 32)); tw_sum=$((tw_sum + $1))
  done
  echo $(( ( (tw_all - tw_n) / 3 * 1000 + tw_sum ) * tw_px / 1000 ))
}
draw_value() {   # y (top of the row) value: right-aligned, ends VAL_GAP px before the ">"
  fb_rect $VAL_L $(($1 + 8)) $((CHV_X - VAL_L - 4)) $((ROW_H - 16)) WHITE
  vw=$(text_w $VAL_PX "$2" B); vx=$((CHV_X - VAL_GAP - vw))
  fb_text_at $VAL_PX $(($1 + (ROW_H - VAL_PX * 13 / 10) / 2)) $vx $IN_R BOLD BLACK "$2"   # wide right limit: never wrap
}
draw_row() {   # y label value
  fb_text_at $LABEL_PX $(($1 + (ROW_H - LABEL_PX * 13 / 10) / 2)) $IN_L $((VAL_L - 20)) REGULAR BLACK "$2"
  draw_value "$1" "$3"
  fb_text_at $VAL_PX $(($1 + (ROW_H - VAL_PX * 13 / 10) / 2)) $CHV_X $IN_R REGULAR GRAY6 ">"
}
draw_switch() {   # y0 state(1 on, 0 off, w waiting): the value of the first row
  case "$2" in 1) t=$T_ON ;; w) t=$T_WAIT ;; *) t=$T_OFF ;; esac
  draw_value "$1" "$t"
}
draw_card() {   # y0 name sub ready running port mode custom-password-flag
  y0=$1; nm=$2; sub=$3; rdy=$4; run=$5; p=$6; md=$7; hf=$8
  fb_text_at $TITLE_PX $((y0 + 4)) $IN_L 500 BOLD BLACK "$nm"
  if [ "$rdy" != 1 ]; then
    fb_text_at $VAL_PX $((y0 + 66)) $IN_L 1100 REGULAR GRAY6 "$T_NOINST"
    return
  fi
  fb_text_at $SUB_PX $((y0 + 62)) $IN_L 500 REGULAR GRAY6 "$sub"
  draw_value "$y0" "$([ "$run" = 1 ] && echo "$T_ON" || echo "$T_OFF")"
  fb_text_at $VAL_PX $((y0 + (ROW_H - VAL_PX * 13 / 10) / 2)) $CHV_X $IN_R REGULAR GRAY6 ">"
  draw_row $((y0 + ROW_P)) "$T_PORT" "$p"
  draw_row $((y0 + ROW_M)) "$T_LOGIN" "$(mode_label "$md" "$hf")"
  if [ "$run" = 1 ]; then
    if [ -n "$IP" ]; then fb_text_at $CMD_PX $((y0 + ROW_C)) $IN_L $IN_R BOLD BLACK "ssh -p $p root@$IP"
    else fb_text_at $CMD_PX $((y0 + ROW_C)) $IN_L $IN_R BOLD BLACK "ssh -p $p root@<ip>"; fi
  else
    fb_text_at $CMD_PX $((y0 + ROW_C)) $IN_L $IN_R REGULAR GRAY6 "$T_OFFTXT"
  fi
}

draw_page() {
  load_state
  "$FBINK" -q -b -c 2>>"$FBINK_LOG"
  BG_USED=""
  if [ -n "$BG_PAGE" ]; then fb_image "$BG_PAGE" 0 0 0 0; BG_USED=1
  else
    draw_frame; fb_hline $Y_D1; fb_hline $Y_D2; fb_hline $Y_D3; fb_hline $Y_D4
    fb_box $CL_X $CL_Y $CL_S $CL_S; fb_text_center 40 $((CL_Y + 8)) $CL_X $((CL_X + CL_S)) BOLD BLACK "×"
  fi
  # block 1: logo on the left, small round close button on the right (the button is part of the background)
  if [ -n "$LOGO" ]; then fb_image "$LOGO" $IN_L $LOGO_Y $LOGO_W $LOGO_H; else fb_text_at 56 $((LOGO_Y + 10)) $IN_L 500 BOLD BLACK "EMBRITH"; fi
  # block 2: title on the left, Wi-Fi address on the right
  fb_text_at $TITLE_PX $Y_T $IN_L 500 BOLD BLACK "$T_TITLE"
  if [ -n "$IP" ]; then wt="$T_WIFI  $IP"; wc=BLACK; else wt=$T_NOWIFI; wc=GRAY6; fi
  ww=$(text_w $INFO_PX "$wt")   # right-aligned, same right edge as the values
  fb_text_at $INFO_PX $((Y_T + 8)) $((CHV_X - VAL_GAP - ww)) $IN_R REGULAR $wc "$wt"
  # blocks 3 and 4: one per source
  draw_card $Y_E "$T_EMB" "$T_EMBSUB" "$E_RDY" "$E_RUN" "$E_PORT" "$E_MODE" "$E_HF"
  draw_card $Y_K "$T_KO" "$T_KOSUB" "$K_RDY" "$K_RUN" "$K_PORT" "$K_MODE" "$K_HF"
  # notes at the bottom of the frame
  fb_text_at $NOTE_PX $NOTE_Y $IN_L $IN_R REGULAR GRAY6 "$T_NOTE1"
  fb_text_at $NOTE_PX $((NOTE_Y + NOTE_GAP)) $IN_L $IN_R REGULAR GRAY6 "$T_NOTE2"
  fb_text_at $NOTE_PX $((NOTE_Y + 2 * NOTE_GAP)) $IN_L $IN_R REGULAR GRAY6 "$T_NOTE3"
  commit_screen
}

# what a tap at (x, y) means: exit | toggle:<src> | port:<src> | auth:<src> | nothing; a tap anywhere in a row counts
hit() {
  x=$(($1 - X_OFF)); y=$2
  if [ "$x" -ge 1090 ] && [ "$y" -le 255 ]; then echo exit; return; fi
  [ "$x" -ge $((FR_X + 10)) ] && [ "$x" -le $((FR_X + FR_W - 10)) ] || { echo nothing; return; }
  for s in embrith:$Y_E:$E_RDY koreader:$Y_K:$K_RDY; do
    sn=${s%%:*}; r=${s#*:}; y0=${r%%:*}; rdy=${r#*:}
    [ "$rdy" = 1 ] || continue
    if [ "$y" -ge $((y0 - 6)) ] && [ "$y" -lt $((y0 + ROW_P)) ]; then echo "toggle:$sn"; return; fi
    if [ "$y" -ge $((y0 + ROW_P)) ] && [ "$y" -lt $((y0 + ROW_M)) ]; then echo "port:$sn"; return; fi
    if [ "$y" -ge $((y0 + ROW_M)) ] && [ "$y" -lt $((y0 + ROW_M + ROW_H)) ]; then echo "auth:$sn"; return; fi
  done
  echo nothing
}

# ---- port entry: a drawn numeric keypad ----
KEYS="1 2 3 4 5 6 7 8 9 del 0 ok"
draw_keyvalue() {   # value message (the part that changes while typing)
  fb_rect $((KP_X + 12)) $((KP_I + 12)) $((KP_FW - 24)) $((KP_IH - 24)) WHITE
  fb_btn_text 64 $KP_I $KP_IH $KP_X $((KP_X + KP_FW)) BOLD BLACK "${1:--}"
  fb_rect $KP_X $KP_M $KP_FW 40 WHITE
  fb_text_center 26 $((KP_M + 4)) $KP_X $((KP_X + KP_FW)) REGULAR GRAY6 "$2"
}
draw_keypad() {   # title value message
  "$FBINK" -q -b -c 2>>"$FBINK_LOG"
  BG_USED=""
  if [ -n "$BG_PAD" ]; then fb_image "$BG_PAD" 0 0 0 0; BG_USED=1; else draw_frame; fb_box $KP_X $KP_I $KP_FW $KP_IH; fb_box $KP_X $KP_CY $KP_FW $KP_CH; fi
  fb_text_center $TITLE_PX $KP_T 0 $LOGICAL_W BOLD BLACK "$1"
  draw_keyvalue "$2" "$3"
  i=0
  for k in $KEYS; do
    kx=$((KP_X + (i % 3) * (KP_W + KP_GX))); ky=$((KP_Y + (i / 3) * (KP_H + KP_GY))); i=$((i + 1))
    case "$k" in del) lb=$T_DEL ;; ok) if [ -n "$BG_USED" ]; then lb=""; else lb=OK; fi ;; *) lb=$k ;; esac   # the check mark is drawn in ssh_keypad_bg.png (the font has no glyph for it)
    fb_button $kx $ky $KP_W $KP_H; [ -z "$lb" ] || fb_btn_text $KEY_PX $ky $KP_H $kx $((kx + KP_W)) BOLD BLACK "$lb"
  done
  fb_btn_text 36 $KP_CY $KP_CH $KP_X $((KP_X + KP_FW)) BOLD BLACK "$T_CANCEL"
  fb_text_center $KP_NPX $KP_NY $IN_L $IN_R REGULAR GRAY6 "$T_KN1"
  fb_text_center $KP_NPX $((KP_NY + KP_NGAP)) $IN_L $IN_R REGULAR GRAY6 "$T_KN2"
  fb_text_center $KP_NPX $((KP_NY + 2 * KP_NGAP)) $IN_L $IN_R REGULAR GRAY6 "$T_KN3"
  commit_screen
}
key_hit() {   # 0-9 | del | ok | cancel | nothing
  x=$(($1 - X_OFF)); y=$2
  if [ "$y" -ge $((KP_CY - 10)) ] && [ "$y" -le $((KP_CY + KP_CH + 10)) ] && [ "$x" -ge $KP_X ] && [ "$x" -le $((KP_X + KP_FW)) ]; then echo cancel; return; fi
  if [ "$x" -lt $KP_X ] || [ "$y" -lt $KP_Y ]; then echo nothing; return; fi
  cx=$(( (x - KP_X) % (KP_W + KP_GX) )); cr=$(( (y - KP_Y) % (KP_H + KP_GY) ))
  c=$(( (x - KP_X) / (KP_W + KP_GX) )); r=$(( (y - KP_Y) / (KP_H + KP_GY) ))
  if [ "$c" -gt 2 ] || [ "$r" -gt 3 ] || [ "$cx" -ge $KP_W ] || [ "$cr" -ge $KP_H ]; then echo nothing; return; fi
  set -- $KEYS; shift $((r * 3 + c)); echo "$1"
}
ask_port() {   # src current-port other-port: sets PORT_NEW; returns 0 = new port, 1 = cancelled, 2 = leave the page
  as=$1; oth=$3; val=""; msg=$T_RANGE
  if [ "$as" = embrith ]; then ttl="$T_EMB  $T_PORT"; else ttl="$T_KO  $T_PORT"; fi
  draw_keypad "$ttl" "" "$msg"
  while wait_tap; do
    k=$(key_hit "$TX" "$TY")
    case "$k" in
      [0-9]) [ "${#val}" -lt 5 ] || continue; val=$val$k; msg=$T_RANGE ;;
      del) val=${val%?}; msg=$T_RANGE ;;
      ok)
        n=$(echo "$val" | sed 's/^0*//')
        if [ -z "$n" ] || [ "$n" -gt 65535 ]; then msg=$T_BAD
        elif [ "$n" = "$oth" ]; then msg=$T_DUP
        else PORT_NEW=$n; return 0; fi ;;
      cancel) return 1 ;;
      *) continue ;;
    esac
    draw_keyvalue "$val" "$msg"
    refresh_part $KP_X $KP_I $KP_FW $((KP_M + 44 - KP_I))
  done
  return 2
}

# an input_event is 16 bytes: sec(4) usec(4) type(2) code(2) value(4); od prints 8 numbers: type is $5, code $6, value $7 and $8
# waits for a finger lift, leaves its position in TX / TY; returns 1 after 5 idle minutes or when the screen is locked
wait_tap() {
  x=""; y=""; idle=0
  if [ -n "$TAPD_PID" ]; then   # kutil tapd: one line per tap ("T x y") or per idle period ("I"), the touch screen is grabbed
    while :; do
      read -r tk ta tb <&4 || return 1
      case "$tk" in
        T) TX=$ta; TY=$tb; return 0 ;;
        I) idle=$((idle + 1)); [ "$idle" -ge 10 ] && return 1
           st=$(lipc-get-prop com.lab126.powerd state 2>/dev/null)
           [ -n "$st" ] && [ "$st" != active ] && return 1 ;;
      esac
    done
  fi
  while :; do
    set -- $({ timeout 30 dd bs=16 count=1 <&3 2>/dev/null | od -An -v -tu2; } 2>/dev/null)
    if [ $# -ne 8 ]; then
      idle=$((idle + 1)); [ "$idle" -ge 10 ] && return 1
      st=$(lipc-get-prop com.lab126.powerd state 2>/dev/null)
      [ -n "$st" ] && [ "$st" != active ] && return 1
      continue
    fi
    idle=0
    if [ "$5" = 3 ]; then
      [ "$6" = 53 ] && x=$7
      [ "$6" = 54 ] && y=$7
      if [ "$6" = 57 ] && [ "$7" = 65535 ] && [ "$8" = 65535 ] && [ -n "$x" ] && [ -n "$y" ]; then TX=$x; TY=$y; return 0; fi
    elif [ "$5" = 1 ] && [ "$6" = 330 ] && [ "$7" = 0 ]; then
      if [ -n "$x" ] && [ -n "$y" ]; then TX=$x; TY=$y; return 0; fi
      x=""; y=""
    fi
  done
}

# ---- run ----
restore_ui() {
  [ -n "$TAPD_PID" ] && kill "$TAPD_PID" 2>/dev/null
  TAPD_PID=""
  exec 4<&-
  exec 3<&-
  [ "$(cat "$LOCKP" 2>/dev/null)" = "$$" ] && rm -f "$LOCKP"
  lipc-set-prop com.lab126.winmgr hideStatusBar 0 >/dev/null 2>&1
  lipc-set-prop com.lab126.powerd preventScreenSaver 0 >/dev/null 2>&1
}
trap restore_ui EXIT
trap 'exit 1' INT TERM HUP

# single instance: a second launch (the launcher can fire twice) takes over, the old one is killed
LOCKP="$S/ssh-page.pid"
op=$(cat "$LOCKP" 2>/dev/null)
if [ -n "$op" ] && [ "$op" != "$$" ] && kill -0 "$op" 2>/dev/null; then
  kill "$op" 2>/dev/null; sleep 1; kill -9 "$op" 2>/dev/null
fi
echo "$$" > "$LOCKP"

if pidof kdispd >/dev/null 2>&1; then
  "$FBINK" -q -y 10 "Embrith (Android) is running: use its settings"
  exit 0
fi

lipc-set-prop com.lab126.powerd preventScreenSaver 1 >/dev/null 2>&1
lipc-set-prop com.lab126.winmgr hideStatusBar 1 >/dev/null 2>&1
# exclusive touch reader (kutil tapd) when this build has it: the library underneath gets no taps. Else read the device directly (taps leak).
TAPD_PID=""
KUTIL=$D/bin/kutil
if [ -x "$KUTIL" ] && "$KUTIL" 2>&1 | grep -q tapd; then
  TFIFO=/var/tmp/embrith-ssh-tap.$$; rm -f "$TFIFO"
  if mkfifo "$TFIFO" 2>/dev/null; then
    "$KUTIL" tapd "$TOUCH" 30 > "$TFIFO" 2>>"$S/logs/ssh-page.log" &
    TAPD_PID=$!
    exec 4<"$TFIFO"
    rm -f "$TFIFO"
  fi
fi
[ -n "$TAPD_PID" ] || exec 3<"$TOUCH" || exit 1

draw_page
while wait_tap; do
  act=$(hit "$TX" "$TY")
  case "$act" in
    exit) break ;;
    toggle:*)
      s=${act#toggle:}
      if [ "$s" = embrith ]; then y0=$Y_E; run=$E_RUN; else y0=$Y_K; run=$K_RUN; fi
      draw_switch "$y0" w; refresh_part $VAL_L "$y0" $((CHV_X - VAL_L)) $ROW_H
      if [ "$run" = 1 ]; then sh "$TOGGLE" off "$s" </dev/null >/dev/null 2>&1; else sh "$TOGGLE" on "$s" </dev/null >/dev/null 2>&1; fi ;;
    port:*)
      s=${act#port:}
      if [ "$s" = embrith ]; then cur=$E_PORT; oth=$K_PORT; else cur=$K_PORT; oth=$E_PORT; fi
      ask_port "$s" "$cur" "$oth"; rc=$?
      [ "$rc" = 2 ] && break
      [ "$rc" = 0 ] && sh "$TOGGLE" port "$s" "$PORT_NEW" </dev/null >/dev/null 2>&1 ;;
    auth:*)
      s=${act#auth:}
      if [ "$s" = embrith ]; then cur=$E_MODE; y0=$Y_E; else cur=$K_MODE; y0=$Y_K; fi
      case "$cur" in key) nm=password ;; password) nm=none ;; *) nm=key ;; esac
      draw_value $((y0 + ROW_M)) "$T_WAIT"; refresh_part $VAL_L $((y0 + ROW_M)) $((CHV_X - VAL_L)) $ROW_H
      if [ "$s" = embrith ] && [ "$nm" = password ]; then sh "$TOGGLE" auth embrith password = </dev/null >/dev/null 2>&1   # keep a custom password if one is set
      else sh "$TOGGLE" auth "$s" "$nm" </dev/null >/dev/null 2>&1; fi ;;
    *) continue ;;
  esac
  if [ -n "$TAPD_PID" ]; then { timeout 1 dd bs=4096 count=1 <&4 >/dev/null 2>&1; } 2>/dev/null
  else { timeout 1 dd bs=16 <&3 >/dev/null 2>&1; } 2>/dev/null; fi    # taps that piled up during the action
  draw_page
done

# leave: the same way as the reading-time page (open the library, a watchdog falls back to the home page)
EXIT_TO="app://com.lab126.KPPMainApp?view=KPP_LIBRARY"
if command -v sqlite3 >/dev/null 2>&1 && [ -r /var/local/appreg.db ]; then
  lib=$(sqlite3 -readonly -noheader /var/local/appreg.db "SELECT handlerId FROM handlerIds WHERE handlerId LIKE '%booklet%library%' ORDER BY handlerId LIMIT 1" 2>/dev/null)
  [ -n "$lib" ] && EXIT_TO="app://$lib"
fi
restore_ui
lipc-set-prop com.lab126.appmgrd start "$EXIT_TO" >/dev/null 2>&1
( sleep 6
  cur=$(lipc-get-prop com.lab126.appmgrd activeApp 2>/dev/null)
  case "$cur" in *booklet*|*KPP*) : ;; *) lipc-set-prop com.lab126.appmgrd start "app://com.lab126.booklet.home" >/dev/null 2>&1 ;; esac ) </dev/null >/dev/null 2>&1 &
exit 0
