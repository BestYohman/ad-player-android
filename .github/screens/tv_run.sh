#!/bin/bash
# Runs inside the emulator job: starts the stand-in PC, installs the app, and takes screenshots of each page.
cd "$(dirname "$0")/../.."
mkdir -p shots
python3 .github/screens/mock.py > shots/mock.log 2>&1 &
sleep 2
P=com.bestyohman.adplayer
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
shot() { sleep "${2:-3}"; adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml shots/ui.xml >/dev/null 2>&1; }
tapon() {   # tap the first thing whose text or description is $1 (or whose class ends in $1)
  dump
  xy=$(python3 - "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
want = sys.argv[1]
nodes = list(ET.parse("shots/ui.xml").iter("node"))
hit = [n for n in nodes if want in (n.get("text"), n.get("content-desc")) or n.get("class", "").endswith(want)] or \
      [n for n in nodes if want in (n.get("text") or "") or want in (n.get("content-desc") or "")]
if hit:
    a, b, c, d = map(int, re.findall(r"\d+", hit[0].get("bounds"))); print((a + c) // 2, (b + d) // 2)
PY
)
  [ -n "$xy" ] && adb shell input tap $xy && echo "tapped $1 at $xy" || echo "NOT FOUND: $1"
}
open() { adb shell "am start -W -a android.intent.action.VIEW -d 'adplayer://open?$1' $P" >/dev/null; }
adb shell am start -W -n $P/.MainActivity
key() { adb shell input keyevent "$@"; sleep 1; }
shot tv_00_server 8
tapon EditText; sleep 2
adb shell input text "10.0.2.2:8080"; sleep 1
key 66; sleep 6
tapon EditText; sleep 2
adb shell input text "yoh"; sleep 1
key 66; adb shell input text "pw"; sleep 1
key 66
sleep 3; key 111          # (the keyboard the sign-in left open)
shot tv_01_sight 8
key 20; key 20
shot tv_01b_sight_focus 2
tapon "Full vision"
shot tv_02_home 8
key 22; key 22
shot tv_03_home_focus 2
key 20; key 20
shot tv_04_rows 3
open "kind=item&id=m_magic&extra=Movie&title=Moonlit%20Magic%202"
shot tv_05_movie 5
key 111
tapon Play
sleep 5
key 111
key 19
dump; grep -q 'resource-id="com.android.inputmethod\|leanback.*ime' shots/ui.xml && key 111
shot tv_06_player_controls 3
tapon "Continue on a phone"
shot tv_07_send_to_phone 6
dump; cp shots/ui.xml shots/ui_send.xml
key 4
sleep 2
key 4
shot tv_08_after 3
open "kind=settings"
shot tv_09_you 4
adb logcat -d -t 400 > shots/logcat.txt
