# AD Player for Android

The Android app for the AD Player in `describealign_ultimate.py`. It talks to the PC the same way the AD Player
web page does (port 5021 at home, or your web address away from home).

- Each person signs in with their own Emby user name and password.
- Play on a TV, or on this phone: just the audio (audio description), or the video for people set to watch.
- People who watch the video get posters, like the Emby app; people who only listen get the plain list,
  made for TalkBack.
- Audiobooks from Audiobookshelf, with chapters, speed, sleep timer and the lock-screen controls.
- Play together: start something with Play together on, and the other person gets a Join button. Play, pause,
  skipping, chapters and the next episode stay in step.
- Keeps playing with the screen off, with the lock-screen, notification and headset controls.

## Getting the app on the phone

GitHub builds the app by itself every time this folder changes (Actions tab - a green tick means it worked).

1. On the phone, open this repository's page in the browser, then **Releases** > **AD Player (latest)**.
2. Tap **ADPlayer.apk** to download it, then open it.
3. The first time, Android asks to allow installing apps from the browser: tap **Settings**, turn on
   **Allow from this source**, go back, and tap **Install**.
4. Open **AD Player**, type the PC's address (the same one the web player uses), and sign in.

A newer build installs over the old one the same way; the address and sign-in are kept.

## Building it yourself (not needed)

`gradle :app:assembleRelease` with JDK 17 and the Android SDK. The app is signed with `app/adplayer.keystore`
(password `adplayer`), so every build can update the one already installed.
