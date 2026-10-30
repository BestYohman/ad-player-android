#!/bin/bash
# Runs inside the emulator job: starts the stand-in PC, installs the app, and takes screenshots of each page.
cd "$(dirname "$0")/../.."
mkdir -p shots
python3 .github/screens/mock.py > shots/mock.log 2>&1 &
sleep 2
P=com.bestyohman.adplayer
adb install -r app/build/outputs/apk/release/app-release.apk
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
shot 00_server 6
tapon EditText
adb shell input text "10.0.2.2:8080"
adb shell input keyevent 66
sleep 5
tapon EditText
adb shell input text "yoh"
adb shell input keyevent 66
sleep 1
adb shell input text "pw"
adb shell input keyevent 66
shot 01_home 10
adb shell input swipe 540 1900 540 700 600
shot 02_home_more 3
adb shell input swipe 540 1900 540 700 600
shot 03_home_end 3
open "kind=item&id=s_chase&extra=Series&title=The%20Great%20Chase"
shot 04_series 5
adb shell input swipe 540 1900 540 900 600
shot 05_series_more 3
open "kind=item&id=se1&extra=Season&title=Season%201"
shot 06_season 5
open "kind=item&id=m_magic&extra=Movie&title=Moonlit%20Magic%202"
shot 07_movie 5
tapon Play
sleep 6
tapon "Got it"
shot 08_player 3
adb shell input tap 540 1200
shot 09_player_hidden 2
adb shell input tap 540 1200
sleep 1
tapon Chapters
shot 10_chapters 3
adb shell input keyevent 4
sleep 1
tapon Info
shot 10b_info 3
adb shell input keyevent 4
sleep 1
adb shell input keyevent 4
shot 11_back_with_mini 3
open "kind=home"
sleep 2
tapon Menu
shot 12_drawer 3
adb shell input keyevent 4
open "kind=search"
sleep 2
tapon EditText
adb shell input text "chase"
shot 13_search 4
open "kind=downloads"
shot 14_downloads 3
open "kind=books"
shot 15_books 4
open "kind=settings"
shot 16_settings 3
adb shell input keyevent 4
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
open "kind=item&id=se1&extra=Season&title=Season%201"
shot 16b_season_landscape 4
tapon "Divide and Conquer"
shot 17_player_landscape 8
adb shell input tap 1200 540
shot 18_player_landscape_tap 2
adb logcat -d -t 400 > shots/logcat.txt
