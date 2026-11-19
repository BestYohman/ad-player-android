# AD Player for Android - what changed

Each version below is a release on GitHub (Releases, "v1.8.0" and so on). The newest is also "AD Player (latest)".

## 1.11.0 - 2026-11-19
- **Safer sign-in at home:** the app only uses the PC's home address once it has proved it's your PC, with a secret key the phone got when you signed in (needs the PC's app 2.30.4). Your sign-in is never sent to an address that hasn't proved it, and never follows a redirect. An address typed without http:// or https:// uses https unless it's on the home network. The sign-in isn't copied to a backup or a new phone.
- **Sign-in:** a **Show password** box, and the phone's password manager can fill in your user name and password.
- **Downloads** wait out a busy or restarting PC and carry on where they stopped. New setting **Download on Wi-Fi only** (on to start). The app says when there isn't enough space on the phone.
- **TalkBack** says "Playing" or "Paused" after Play or Pause, and how a download is going (getting ready, 25, 50, 75 percent, downloaded). The filter switches say if they're on or off, the chosen sort or filter is read as selected, "Sort By" and "Filters" are headings, and the filters say "Loading…" while their choices come.
- **Large text:** rows of buttons wrap onto the next line, and Audiobooks, Shows and Movies are no longer cut short.
- **While browsing**, a small bar shows what's playing, with its picture and Play / Pause.
- **Pull down** on Home or a library to bring it up to date.
- **Outside controls:** a stop from Hey Google, the headset, the car or the notification saves your place. Previous and next there are 30 seconds back and forward; chapters and episodes are chosen in the app. After a restart, Play on the headset or in the car carries on with the last book or audio description. "Hey Google, play ... on AD Player" works.
- **Play together:** headphones out pauses for everyone. A phone that lost the PC joins again by itself, at the same place as the others. Stop asks "Stop for everyone?" first.
- **The soundtrack** is only played when playing together or when you chose one without audio description, and the app says so.
- **Android Auto:** pictures, Continue loads faster, lists look right, and an empty list says why.
- **Updates:** a rebuilt version reaches the phone too, the download is checked before it installs, and Install is greyed out while it downloads.
- Ready for a new signing key: the update installs over the old app as usual.

## 1.10.0 - 2026-11-06
- **Play together** is a button beside each Play (episodes, movies, books, and on a show's, movie's or book's page) instead of a switch. With a TV chosen, Play together moves to the phone by itself. The headphone delay stays in Settings.
- The library **filters** have their Genre, Parental rating and Year choices again (the PC now collects them from the library itself - needs the PC's app 2.26.0).
- An episode or movie without audio description plays its soundtrack on the phone.

## 1.9.0 - 2026-11-04
- **Updates:** when a new version is on GitHub, Home shows "Version … is ready" with what's new and an **Install** button (the phone asks once to allow installing from AD Player). Settings shows the app's version and can check by hand.
- **Downloads tidy themselves** (Settings, both off to start): **Remove downloads once finished**, and **Download the next episode on Wi-Fi** when a downloaded episode ends.

## 1.8.1 - 2026-11-04
- The side menu showed Audiobooks twice; now once (and not at all when Audiobooks is hidden on the PC).

## 1.8.0 - 2026-11-04
- Movies and TV shows: **Sort** (title, date added, date played, release date, year, runtime, critic rating, community rating, parental rating, plays, random - tap again to flip the order) and **Filter** (played / unplayed / in progress, favorites, has audio description, genre, parental rating, year). Each library remembers its own.
- Home: **Audiobooks** under My Media, and a **Latest Audiobooks** row.
- Libraries hidden on the PC (Settings, AD Player, "Libraries the player hides") no longer show.
- The new round AD logo as the app icon, filling the circle.

## 1.7.0 - 2026-11-04
- Android Auto: tabs **Continue** (books, shows and movies together, most recent first), **Recent** (the newest books, episodes and movies), **Libraries** (Audiobooks, Shows, Movies) and **Downloads**, each with an icon; posters for shows and movies.
- A book or the audio description pauses while the car's navigation speaks, and carries on after.
- The app icon fills the circle in Android Auto and on round home screens.

## 1.6.0 - 2026-11-03
- Android Auto audiobooks: **back 30 s**, **forward 30 s**, **Stop** and **Add a bookmark** buttons; previous / next chapter on the skip buttons.
- The car shows the book's title, then the chapter (number and name) and the time played of the whole book; the bar runs through the chapter.
- Bookmarks from the car are named by themselves (the chapter and the time).
- Pause in the car now shows as paused and the time stops.

## 1.5.0 - 2026-11-02
- Downloads show what's happening: the PC making the file (copying or converting), how much is done, the size, the speed in Mbps and the time left; why a download is paused or failed.
- The app downloads by itself (Android 17's downloader couldn't reach the PC at home): straight from the PC on the home Wi-Fi, through the web address away, carrying on where it stopped and moving over when the network changes, with a notification while it works.

## 1.4.0 - 2026-11-02
- Audiobook bookmarks (Audiobookshelf's own): add one where you are, list them, jump to one, rename, remove.
- Saved places: the newest one wins (the phone and the PC are compared); places saved with no connection are sent when it's back.

## 1.3.0 - 2026-10-30
- An audio player like Emby's; book and episode pages; in a season the picture plays and the words open the page; downloads play from the phone straight away.
- Play together: Stop stops everyone; nobody is skipped ahead (the others wait); the listener's audio is on the phone before it starts; after a skip it waits until every phone has caught up.
- Books are fetched a piece at a time, so they start quickly.

## 1.2.0 - 2026-10-29
- Looks and works like the Emby app for people who watch the video: home, libraries, show / season / movie pages, search, and a video player with Emby's controls (also sideways).

## 1.1.0 - 2026-10-29
- Download for later, with a choice of video quality (original, medium or small); stopping a download tells the PC to stop and delete its copy.
- Android Auto (audio description and audiobooks only).
- Headphone delay setting, and join links for Play together.

## 1.0.0 - 2026-10-29
- First version: sign in, play the audio description or the video on the phone or a TV, Play together, playback speed.
- Steadier on phone networks; connects straight to the PC on the home Wi-Fi.
