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
shot 00_server 6
for try in 1 2 3; do
  tapon EditText; sleep 2
  adb shell input text "10.0.2.2:8080"; sleep 1
  adb shell input keyevent 66; sleep 6
  dump; grep -q 'Use your Emby' shots/ui.xml && break
  adb shell input keyevent KEYCODE_MOVE_END; for i in $(seq 1 30); do adb shell input keyevent 67; done
done
tapon EditText; sleep 2
adb shell input text "yoh"; sleep 1
adb shell input keyevent 66; sleep 1
adb shell input text "pw"; sleep 1
adb shell input keyevent 66
shot 00b_sight 8
tapon "Full vision"
shot 01_home 8
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
tapon "Audio and subtitles"
shot 10a_tracks 3
adb shell input keyevent 4
sleep 1
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
adb shell input tap 540 1200
sleep 1
tapon "Stats for nerds"
shot 10c_stats 5
tapon "Close stats"
sleep 1
adb shell input keyevent 4
shot 11_back_with_mini 3
open "kind=home"
sleep 2
tapon Shows
shot 12_shows 6
tapon You
shot 12b_you 3
open "kind=search"
sleep 2
tapon EditText
adb shell input text "chase"
shot 13_search 4
open "kind=item&id=m_magic&extra=Movie&title=Moonlit%20Magic%202"
sleep 4
tapon Download
sleep 2
tapon "The video - small"
sleep 2
open "kind=downloads"
shot 14_downloads 8
shot 14b_downloads_later 30
shot 14c_downloads_done 25
open "kind=books"
shot 15_books 4
tapon "The Bank Job"
shot 15b_book_page 4
tapon Resume
shot 15c_audio_player 6
# what the car and lock screen are told (title, the bar, the buttons)
adb shell dumpsys media_session > shots/media_session.txt 2>&1 || true
sleep 45
adb shell dumpsys media_session > shots/media_session_later.txt 2>&1 || true
adb shell cmd media_session dispatch pause >/dev/null 2>&1 || adb shell input keyevent 127
sleep 4
adb shell dumpsys media_session > shots/media_session_paused.txt 2>&1 || true
adb shell cmd media_session dispatch play >/dev/null 2>&1 || adb shell input keyevent 126
adb shell input keyevent 4
sleep 1
open "kind=item&id=e1&extra=Episode&title=The%20Race%20Begins"
shot 15d_episode_page 5
open "kind=settings"
shot 16_settings 3
adb shell input keyevent 4
open "kind=item&id=m_magic&extra=Movie&title=Moonlit%20Magic%202"
sleep 3
tapon Play
sleep 5
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
shot 17_player_landscape 5
adb shell input tap 1200 540
shot 18_player_landscape_tap 2
adb shell input keyevent 4
shot 19_movie_landscape 3
adb shell input keyevent 4
adb shell settings put system user_rotation 0
sleep 3
# play together: who to invite, then what's going on
open "kind=item&id=m_magic&extra=Movie&title=Moonlit%20Magic%202"
sleep 4
tapon "Play together"
shot 40_invite_pick 6
tapon "Ann"
tapon "Invite"
shot 41_together 6
adb shell input tap 540 1200
sleep 1
shot 41b_together_controls 2
sleep 25
adb shell input tap 540 1200
shot 42_together_playing 2
tapon "Stop for everyone"
sleep 2
dump; grep -q 'Stop for everyone' shots/ui.xml && tapon "Stop for everyone"
sleep 2
adb shell input keyevent 4
curl -s localhost:8080/mock/invite >/dev/null
open "kind=home"
sleep 8
shot 43_invited 3
# how she sees the screen: low vision (dark, light, yellow on black), then no vision
open "kind=settings"
sleep 3
tapon "How you see the screen"
shot 20_sight_page 3
tapon "Low vision"
shot 21_low_sight_page 4
adb shell input swipe 540 1900 540 700 600
shot 22_low_sight_more 3
adb shell input swipe 540 1900 540 700 600
shot 23_low_sight_end 3
tapon Home
shot 24_low_home 6
adb shell input swipe 540 1900 540 700 600
shot 25_low_home_more 3
tapon "Press for light"
shot 26_low_light 4
tapon "Press for yellow"
shot 27_low_yellow 4
open "kind=settings"
sleep 3
tapon "How you see the screen"
sleep 2
tapon "Larger"
shot 28_low_larger 4
open "kind=home"
shot 28b_low_home_larger 5
open "kind=item&id=m_magic&extra=Movie&title=Moonlit%20Magic%202"
shot 29_low_movie 5
open "kind=item&id=s_chase&extra=Series&title=The%20Great%20Chase"
shot 30_low_series 5
tapon Movies
shot 31_low_movies 5
tapon You
shot 32_low_you 3
tapon "How you see the screen"
sleep 3
tapon "No vision"
shot 33_no_vision 5
adb shell input swipe 540 1900 540 300 600
adb shell input swipe 540 1900 540 300 600
adb shell input swipe 540 1900 540 300 600
shot 34_no_vision_end 3
dump; cp shots/ui.xml shots/ui_no_vision.xml
adb logcat -d -t 400 > shots/logcat.txt
