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
sleep 8
# --- from here on only the remote's keys (a tap puts the TV into touch mode, which hides the outline) ---
focused() { dump; python3 - <<'PY'
import xml.etree.ElementTree as ET
for n in ET.parse("shots/ui.xml").iter("node"):
    if n.get("focused") == "true" and "ComposeView" not in n.get("class", ""):
        name = n.get("content-desc") or n.get("text") or next((c.get("content-desc") or c.get("text") for c in n.iter("node") if c.get("content-desc") or c.get("text")), "?")
        print(name + "|" + n.get("bounds")); break
PY
}
onscreen() { dump; python3 - "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
for n in ET.parse("shots/ui.xml").iter("node"):
    if sys.argv[1] in ((n.get("text") or "") + "|" + (n.get("content-desc") or "")):
        a, b, c, d = map(int, re.findall(r"-?\d+", n.get("bounds"))); print("yes" if b >= 0 and d <= 1080 and d > b else "no"); break
else: print("no")
PY
}
shopt -s extglob
FAILS=""
expect() { if [[ "$2" == $3 ]]; then echo "OK   $1 ($2)"; else echo "FAIL $1: got '$2'"; FAILS=1; fi | tee -a shots/focus_report.txt; }
keys() { local k=$1 n=$2; for _ in $(seq "$n"); do adb shell input keyevent "$k"; sleep 0.4; done; sleep 1.5; }
: > shots/focus_report.txt

shot tv_02_home 2
# (the tap above leaves the emulator in touch mode, where nothing has the outline until the first key)
keys 20 1
shot tv_03_home_focus 2
expect "down goes into the page" "$(focused)" "Resume*"
expect "the menu slides away in the page" "$(onscreen 'Home, selected')" "no"
keys 20 2
shot tv_04_rows 2
CARD=$(focused | cut -d'|' -f1)
expect "down reaches a card" "$CARD" "?*"
keys 20 15
shot tv_20_home_bottom 2
expect "down reaches the last row" "$(onscreen 'Audiobooks in progress')" "yes"
keys 19 25
shot tv_21_home_top 3
expect "up at the top reaches the menu" "$(focused)" "@(Home*|Shows*|Movies*|Books*|Search*|Settings*)"
expect "the top of the page shows again" "$(onscreen 'CONTINUE WATCHING')" "yes"
# open a card and come back to it
keys 20 3
CARD=$(focused | cut -d'|' -f1)
keys 23 1; sleep 3
shot tv_22_opened 1
expect "an opened page starts on its button" "$(focused)" "@(Play*|Resume*|Episodes*)"
keys 4 1; sleep 2
expect "Back returns to the same card" "$(focused | cut -d'|' -f1)" "$CARD"
keys 4 1
expect "Back from the page goes up to the menu" "$(focused)" "Home*"
# a movie's page: all of it can be reached
open "kind=item&id=m_magic&extra=Movie&title=Moonlit%20Magic%202"
sleep 5
shot tv_05_movie 1
expect "the movie page starts on Play" "$(focused)" "@(Play*|Resume*)"
keys 20 12
shot tv_23_movie_bottom 2
expect "down reaches the cast at the bottom" "$(onscreen 'Cast')" "yes"
keys 19 20
expect "up goes back up to the menu" "$(focused)" "@(Home*|Shows*|Movies*|Books*|Search*|Settings*)"
shot tv_24_movie_top 2
keys 20 1
expect "down from the menu goes to the buttons" "$(focused)" "@(Play*|Resume*|Play on*|Play together*|Add to*|Mark as*)"
# the player: Back hides the buttons, Back again stops it
keys 21 6
expect "left along the buttons reaches Play" "$(focused)" "@(Play *|Resume*)"
keys 23 1
sleep 9
shot tv_06a_player_hidden 1
key 19
shot tv_06_player_controls 1
keys 4 1
shot tv_06b_back_hides 1
expect "Back first hides the player's buttons" "$(onscreen 'Stats for nerds')" "no"
keys 4 1; sleep 2
shot tv_08_after 2
expect "Back again stops the video (back on the page)" "$(focused)" "@(Play*|Resume*)"
# Movies: the title and chips scroll away; Sort and filter can be reached
open "kind=movies"
sleep 5
shot tv_04b_movies 1
expect "the Movies page starts on its first chip" "$(focused)" "With AD*"
F=""; for _ in 1 2 3 4 5; do keys 22 1; F=$(focused); [[ "$F" == Sort* ]] && break; done
expect "Sort and filter can be reached along the chips" "$F" "Sort and filter*"
keys 19 3
expect "up from the chips reaches the menu" "$(focused)" "@(Movies*|Home*|Shows*|Books*)"
shot tv_25_movies_menu 2
# Settings: the TV's size
open "kind=settings"
sleep 4
shot tv_09_you 1
tapon "Screen and size"
shot tv_10_size 4
tapon "Large"
shot tv_11_size_large 4
keys 20 8
tapon "Medium"
shot tv_12_size_medium 4
open "kind=home"
shot tv_13_home 5
adb logcat -d -t 400 > shots/logcat.txt
[ -z "$FAILS" ] && echo "All TV focus checks passed" || echo "::warning::TV focus checks failed (shots/focus_report.txt)"
