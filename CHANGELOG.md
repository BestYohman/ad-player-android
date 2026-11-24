# AD Player for Android - what changed

Each version below is a release on GitHub (Releases, "v1.8.0" and so on). The newest is also "AD Player (latest)".

## 1.14.4 - 2026-11-24
- **Server settings:** whoever runs the AD Player Server (its admins, and the people picked in its Settings) has a Server settings card in Settings. It opens the server's own page inside the app - AD files, the queue, people and the settings - already signed in.

## 1.14.3 - 2026-11-24
- **The bar at the top** stays a floating glass bar as you scroll, as on the web page, with no black strip behind it - on Home and on a show's or movie's page, where it now floats over the big picture.

## 1.14.2 - 2026-11-24
- **Google TV and Android TV, made for the remote:**
  - The menu at the top slides away while you're on a page, so the page has the whole screen. Up from the top of a page, or Back, brings it back (and the page goes back to its very top).
  - Opening a page puts you on its first button (Play or Resume). Back puts you on the card you opened it from.
  - Every part of a page can be reached: Down goes all the way to the bottom (what it's about, the details, the cast, a book's About), and Up all the way back to the big picture at the top.
  - Each row remembers where you were in it. "See all" is a card at the end of each row. On Shows and Movies the name and the chips scroll away with the posters, and Sort and filter is at the end of the chips.
  - In the player, Back first hides the buttons, then stops the video.
  - The outline on what the remote is on shows on white buttons too, and lists start on the current choice.

## 1.14.1 - 2026-11-23
- **Google TV and Android TV:** the menu is along the top (Home, Shows, Movies, Books, Search, Settings, and Play on), like the web page on a computer, instead of down the side.
- **Size on the TV:** everything is smaller on a TV now, and Settings, Screen and size has "Size on this TV" (Small, Medium, Large, Larger) to make it just right. It's kept on that TV only.
- **Back on the TV's remote** stops the video and goes back to the page (while playing together it asks first, like Stop). The Lock button isn't on the TV any more (the remote couldn't unlock it), and the remote's arrows don't get stuck on the position bar.
- **Fixes:** play together ends properly when the sign-in runs out (it kept trying every second); TalkBack no longer reads the small player's clock over and over; a Join from a notification isn't done again when the phone is turned; "Settings" fits in the tabs with big text; the remote starts on the page's own menu item after the player closes.

## 1.14.0 - 2026-11-23
- **Settings** (with a gear) instead of You, in the tabs at the bottom and in the TV's side menu.
- **A movie's, show's or episode's page:** just the big picture at the top (no cover on the left), fading to black so the words on it are easy to read. An episode's picture is the one from that episode, and its show and its season and episode are bold, like its name, with no title line above.

## 1.13.0 - 2026-11-23
- **Google TV and Android TV:** AD Player is on the TV's home screen. A menu down the side (Home, Search, Shows, Movies, Books, You), a white outline on whatever the remote is on, and the remote in the player: OK plays and pauses, left goes back 10 seconds, right ahead 30, up shows the buttons.
- **Your TV, on your phone:** while the TV plays something signed in as you, your phone shows "Your TV is playing ... - Join". Join plays it on the phone in step with the TV, which carries on for everyone (to step away and keep watching or listening). Needs the PC's app 2.45.0.
- **Continue on a phone** (on the TV): playing together starts without stopping the TV, and it's sent to your phone or anyone else who can sign in.
- **Play together:** choose who to invite (people who can sign in to the player). They see the invitation with Join and Not now, and a notification when the app is in the background. While playing together, a line says what's going on (waiting for someone to load, who paused, who lost connection), with what each person's phone is doing and what happened lately.
- **Stats for nerds:** a button in the video player shows how it's played - the picture's size, dropped frames, the connection, how much is loaded ahead, and from the PC whether the video and sound are copied or remade, and why.
- **Play on:** the TVs used last come first. Chromecast / Google TV and Apple TV (AirPlay) are listed (an Apple TV that asks is paired once with the code it shows). Needs the PC's app 2.44.0.
- Posters without the AD mark have no audio description (no "No AD" label any more).

## 1.12.0 - 2026-11-23
- **Looks like the web page:** the same home with the big picture at the top, rows of posters, and Home, Shows, Movies, Books and You along the bottom. Show, movie and book pages, the library pages with Sort and filter, and You (settings) are laid out like the web page too.
- **How you see the screen**, as on the web page: the first sign-in asks once, and it can be changed any time under You. It's kept on the PC, so it's the same everywhere you sign in (needs the PC's app 2.31.0).
  - **No vision:** the audio-only layout made for TalkBack.
  - **Low vision:** big text and buttons, thick outlines, and a dark, light or yellow-on-black screen. Screen is at the top beside Search. Under You: text size (Normal, Large, Larger, Largest), the screen, and switches to show the video, add an extra-thick outline on what's selected, turn off moving animations, and show posters and covers. A show's or movie's page has its words on a plain background and one button a line.
  - **Full vision:** the poster layout.
- **While playing:** a bar at the bottom with Stop, back 10 seconds, Play / Pause, forward 30 seconds, Next, and **Audio and subtitles** (pick the sound track and subtitles of a video, on the phone or the TV).
- The video player's buttons are laid out like the web page's.

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
