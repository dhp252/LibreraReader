![Logo](https://raw.githubusercontent.com/foobnix/LirbiReader/master/logo.jpg)

**The development and support of Librera is frozen for an unpredictable time, there is a big war in my country
Ukraine.**
[Russian invasion of Ukraine](https://en.wikipedia.org/wiki/2022_Russian_invasion_of_Ukraine)

# Librera Reader

Librera Reader is an e-book reader for Android devices;
it supports the following formats: PDF, EPUB, EPUB3, MOBI, DjVu, FB2, TXT, RTF, AZW, AZW3, HTML, CBZ, CBR, DOC, DOCX,
and OPDS Catalogs

# This fork: build and test

This fork adds to upstream Librera:
- the sentence being read aloud is highlighted, and double-tapping a sentence reads from there;
- an annotation toolbar for PDFs (highlight, underline, strikethrough, pen, eraser, undo), and a stylus that draws at once (pen with pressure, highlighter, eraser end);
- WebDAV sync of books and reading state;
- AI chapter and book summaries, and AI-picked key passages (Claude or an OpenAI-compatible server).

The instructions below build the `fdroid` flavor, which has no Google services, on Linux. On
Windows, run them in WSL (Ubuntu 24.04).

## 1. One-time setup

```bash
# tools (the X11/GL headers are needed by MuPDF's host build)
sudo apt-get install -y build-essential pkg-config git zip unzip openjdk-21-jdk-headless \
  mesa-common-dev libxcursor-dev libxrandr-dev libxinerama-dev libglu1-mesa-dev libxi-dev libgl-dev

# Android SDK command-line tools in ~/Android/Sdk/cmdline-tools/latest, then the NDK
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/Android/Sdk "ndk;28.2.13676358"
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# a signing key (an installed APK only updates over an APK signed with the same key: back it up)
keytool -genkeypair -storetype PKCS12 -keystore ~/.android/librera-release.p12 -alias librera \
  -keyalg RSA -keysize 2048 -validity 10000
```

`app/build.gradle` reads these properties even for debug builds. Put them in `~/.gradle/gradle.properties`:

```properties
librera_ndk_dir=/home/YOU/Android/Sdk/ndk/28.2.13676358
librera_java_home=/usr/lib/jvm/java-21-openjdk-amd64
RELEASE_STORE_FILE=/home/YOU/.android/librera-release.p12
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=librera
RELEASE_KEY_PASSWORD=...
# empty is fine for fdroid: pro_appGdriveKey, and for each flavor librera, pdf_classic, ebooka,
# pdf_v2, tts_reader, epub_reader: <flavor>_appGdriveKey, <flavor>_admobAppId,
# <flavor>_admobBannerId, <flavor>_admobFullId, <flavor>_admobRewardId
pro_appGdriveKey=
librera_appGdriveKey=
# ... and the rest of the list above
```

On Windows, keep the checkout's shell scripts in LF, or the native build fails with `$'\r': command not found`:

```bash
git config core.autocrlf false   # or convert gradlew, *.sh, *.mk and Makefile* to LF
```

## 2. Build the native library (MuPDF), once

```bash
cd Builder
./link_to_mupdf_1.28.5.sh      # clones MuPDF 1.28.5, builds libMuPDF.so into app/src/main/jniLibs
cd ..
```

Run it again only after changing `Builder/jni`. Without `libMuPDF.so` the app still builds but
cannot open any book, so check that the APK contains it (step 3).

## 3. Build the APK

```bash
./gradlew assembleFdroidRelease        # or assembleFdroidDebug
unzip -l "app/build/outputs/apk/fdroid/release/"*arm64.apk | grep libMuPDF.so
```

- `release` is minified by R8 and signed with the key from step 1. Use it whenever you try the
  app on a phone, speed included.
- `debug` is debuggable and not optimized: taps, dialogs and page turns are slower than users
  will see. It is for the debugger and for the unit tests (step 4).

"BUILD SUCCESSFUL" alone is not enough: the `unzip` line must list `libMuPDF.so`, or the app
cannot open any book.

An installed app only updates over an APK signed with the same key. To put a release build on a
phone that has a debug one without losing its data, sign it with the debug key:

```bash
./gradlew assembleFdroidRelease \
  -Pandroid.injected.signing.store.file=$HOME/.android/debug.keystore \
  -Pandroid.injected.signing.store.password=android \
  -Pandroid.injected.signing.key.alias=androiddebugkey \
  -Pandroid.injected.signing.key.password=android
```

### Which APK

The APKs land in `app/build/outputs/apk/fdroid/<debug|release>/`, one per CPU type:

| APK | For |
|-----|-----|
| `...-arm64.apk` | almost every phone of the last years: use this one |
| `...-uni.apk` | any device (all four types, about three times bigger) |
| `...-arm.apk`, `...-x86.apk`, `...-x86_64.apk` | old phones, emulators |

### Install it

- With `adb` and USB debugging on: `adb install -r "app/build/outputs/apk/fdroid/release/"*arm64.apk`.
- Without `adb` (WSL does not see USB devices without extra setup): copy the APK to the phone
  (USB "File transfer", or a cloud drive), open it in the phone's Files app and tap Install or
  Update. Allow installs from that app the first time Android asks.

"App not installed" means the app on the phone is signed with another key (the official
Librera, or a build with another key). Build again with the key it was signed with, or uninstall
it first, which deletes its settings.

### On the maintainer's machine (Windows + WSL)

Two helper scripts do steps 1 to 3 from Windows. They live outside the repo, in
`.agents/skills/dev-build-apk/scripts/`: `setup_wsl_env.sh` runs once (already done there), and
`build_apk.sh` builds. From PowerShell:

```powershell
wsl -e bash /mnt/c/Users/DOP07/PROJECTS/private/LibreraReader/.agents/skills/dev-build-apk/scripts/build_apk.sh fdroid release --debug-key
```

From Git Bash, put `MSYS_NO_PATHCONV=1` before `wsl`, or the `/mnt/c/...` path gets rewritten.
The script mirrors the tree to the WSL disk (faster), builds, copies the APKs back and checks
each one for `libMuPDF.so`. A good build ends with:

```
[build] [OK]  Librera Fdroid-9.6.44-mupdf1.28.5-arm64.apk  32M  libMuPDF.so x1
[build] APKs: C:\Users\DOP07\PROJECTS\private\LibreraReader\app\build\outputs\apk\fdroid\release
```

A failure prints a line with `[ERR]`. Arguments: `[flavor] [debug|release]`, then any of:

| Option | Does |
|--------|------|
| `--debug-key` | signs with the debug key, as above (installs over a debug build) |
| `--tests[=pattern,...]` | runs the JVM unit tests first; with `debug` only (there is no release test task) |
| `--clean` | starts from scratch, when a build behaves oddly after big changes |
| `--native` | rebuilds `libMuPDF.so`, only after changing `Builder/jni` (much longer) |

Do not edit `app/src` while a build runs: Gradle compiles whatever is on disk when it starts, and
a half-saved file shows up as compile errors that are not real.

## 4. Run the unit tests

JVM tests, no device needed:

```bash
./gradlew testFdroidDebugUnitTest                                   # everything
./gradlew testFdroidDebugUnitTest --tests 'com.foobnix.ai.*' \
                                  --tests 'com.foobnix.tts.*' \
                                  --tests 'com.foobnix.webdav.*' \
                                  --tests 'com.foobnix.sys.*'       # the fork's tests
```

Reports are written to `app/build/reports/tests/testFdroidDebugUnitTest/index.html`.
Five upstream tests fail in the full run, and the fork does not change them or the code they test:
- `com.TestSync` (4): `MyPath` reads Android APIs while it initializes, which a JVM test cannot do;
- `com.TestPage` (1): it expects older spacing.

The fork's tests all pass.

| Package | Covers |
|---------|--------|
| `com.foobnix.tts` | finding the spoken sentence on the page; where reading restarts after a double tap |
| `com.foobnix.webdav` | two-way folder sync (conflicts, deletions, mass-deletion guard), WebDAV client |
| `com.foobnix.ai` | quote matching, per-book AI store, TOC chapters, prompts, Claude/OpenAI request and stream parsing |
| `com.foobnix.sys` | the crash marks that keep a book which crashed a codec from crashing the app again |

`WebDavServerTest` runs against a real WebDAV server and is skipped when there is none. To run
it, start a local one ([wsgidav](https://github.com/mar10/wsgidav)) and advertise it in
`/tmp/librera-webdav-test.txt`:

```bash
python3 -m venv ~/venvs/wsgidav && ~/venvs/wsgidav/bin/pip install wsgidav cheroot
mkdir -p /tmp/librera-dav-root
cat > ~/wsgidav-test.yaml <<'EOF'
host: 127.0.0.1
port: 8090
provider_mapping:
  "/dav": /tmp/librera-dav-root
http_authenticator:
  accept_basic: true
  accept_digest: true
  default_to_digest: true
simple_dc:
  user_mapping:
    "*":
      "tester":
        password: "s3cret"
EOF
~/venvs/wsgidav/bin/wsgidav --config ~/wsgidav-test.yaml &
printf 'http://127.0.0.1:8090/dav\ntester\ns3cret\n' > /tmp/librera-webdav-test.txt
```

## 5. Check on a device

Unit tests cover the logic; the screens need a phone or tablet. After installing:

- **TTS**: start read-aloud. The spoken sentence is highlighted, and a double tap on another
  sentence continues from there. Try both paged and scroll mode (switches in the TTS dialog).
- **PDF annotation**: open a PDF and tap the edit button in the top bar. Highlight, underline,
  strike, draw, erase and undo. Close the book (it asks to save, unless saving is automatic),
  reopen it and check the annotations are still there.
- **AI colors**: ✦ > Highlight key passages on a non-fiction chapter: the first time it asks what
  you want from the book. Marks come in red, blue, purple, orange, green and grey, never yellow.
  ✦ > Colors shows what each means; tap a mark and its category shows. Messages of the pen
  toolbar now appear above it, not over it.
- **Stylus** (a phone or tablet with an active pen): open a PDF without opening the toolbar and
  write with the pen: it draws at once, thinner or bolder with pressure, while a finger still turns
  pages. Rest your palm while writing: nothing happens. Set "Stylus draws with" to Highlighter
  (reader menu > advanced preferences) and drag over a line of text: the text under it is selected
  as you go and highlighted when you lift (over a picture: nothing, with a message). Hold the side button: the other tool. Tap a mark with the eraser
  end (if the pen has one). Undo after a pressure stroke removes all of it. Save, reopen in another
  PDF reader, check the marks are there.
- **WebDAV**: Preferences > WebDAV sync. Enter the server and Test it. Read a few pages, sync,
  then open the same book on a second device and check that it continues there.
- **One folder for all devices**: update both devices, open the app on each, sync. The server's
  `profile.<name>/` has the files directly, no `device.*` folder. Read a book on one device, leave
  it, open it on the other: it opens at that page without a manual sync. Delete a bookmark or a
  favorite on one, sync both: it stays deleted. Tag a book on one: it is tagged on the other.
  Zoom differently on each: each keeps its own zoom.
- **WebDAV tab**: once WebDAV sync is on, a WebDAV tab appears in the tab bar. It shows the server's Books folder in the Folders tab's grid or list. After a
  sync on the device that has the books, the other device shows their covers, with a cloud mark
  on books that aren't downloaded. Open one: it downloads, opens, and then shows as a local book.
  Also check long-press > Remove from this device, the back key in a subfolder, and pull to refresh.
  Books copied to the server from a computer get their cover and details (title, author,
  series, pages) while the tab shows them: each is downloaded once, whatever its size, then both
  are shared with every device. Check the list mode shows authors for books not downloaded.
  PDF covers must be white, not black. Scroll a folder: the path row scrolls away with it. Go in
  and out of folders: one seen before shows at once, and back returns to the same place.
- **Settings over WebDAV**: change a setting (font size, theme, page turn) on one device, sync it,
  then sync the other: it has the change (theme after a restart). Change different settings on
  both before syncing: both survive. Set the AI key and a master prompt on one device: after a sync
  the other can run AI actions without typing the key.
- **WebDAV tab bar**: filter, the book count, sort by name/date/size (long press reverses), and the
  display menu (list, compact, grid, covers).
- **Back in a book**: back closes the book and shows the library, also for a book opened from a
  file manager or another app. A long press of back still shows the close menu.
- **AI**: tap ✦ in the top bar, then AI settings:
  - Claude: an API key (model `claude-opus-5-5` by default).
  - OpenAI-compatible: server URL, model, and a key if the server needs one.
  - Tap Test.
  
  Then try:
  - "Summary of this chapter": it streams in, and opening it again is instant.
  - "Highlight key passages > This chapter": passages appear in their own color.
  - Show/hide and Remove.
  - On a PDF, "Save AI highlights into PDF".

  The key stays on the device and is never synced or backed up. Book text is sent only when
  you use an AI action.
- **Page turn** (use the release APK; debug builds make animations stutter): in book mode, open
  the page flipping dialog or the advanced preferences and pick each "Page turn" style:
  - Slide, Cover, Page curl: turn forward and back by tap, volume key and swipe. A slow, short
    drag of the curl should fall back; a flick should complete it.
  - Page curl on a page with a TTS or AI highlight: the highlight curls with the page.
  - A right-to-left book (manga): every style is mirrored.
  - "Vibrate lightly when a page turns": a light tick for your own turns, none during autoplay.

# Download application

[Librera on Google Play](https://play.google.com/store/apps/details?id=com.foobnix.pdf.reader)

[Librera PRO on Google Play](https://play.google.com/store/apps/details?id=com.foobnix.pro.pdf.reader)

[Librera F-Droid](https://f-droid.org/en/packages/com.foobnix.pro.pdf.reader/)


### Other Librera Applications

[About LibreraX](https://github.com/foobnix/LibreraReader/blob/master/LIBRERAX.md)

[LibreraX PRO testing for Android](https://github.com/foobnix/LibreraReader/releases/)

[LibreraX FREE testing for iOS, macOS](https://testflight.apple.com/join/XVU24NY9)

[Librera 1](https://librera1.com)

### Links

[FAQ](https://librera.mobi/faq/)

[Librera Community in Telegram](https://t.me/librera_reader_chat)

[Support on Patreon](https://www.patreon.com/librera)

[librera.reader@gmail.com](mailto:librera.reader@gmail.com)

## Required build libs

~~~~
mesa-common-dev libxcursor-dev libxrandr-dev libxinerama-dev libglu1-mesa-dev libxi-dev pkg-config libgl-dev
~~~~

You also need the Android NDK in version 20+
Please ensure to download it using android studio and add the NDK to your PATH.

## Create a keystore

Even if you do not plan to upload a version yourself you need a keystore with a certificate to build.
The keystore needs to be in PKCS12 format.
You can create a keystore in your actual directory using the following call
(replace ALIAS by your alias, it is just a name):

~~~~
keytool -genkey -v -storetype PKCS12 -keystore keystore.pkcs12 -alias ALIAS -keyalg RSA -keysize 2048 -validity 10000
~~~~

Now edit or create the file ~/.gradle/gradle.properties and set following values
(replacing PASSWD by the password you typed while creating the keystore, ALIAS as before and using the path to your
keystore):

~~~~
RELEASE_STORE_FILE=/PATH/TO/YOUR/keystore.pkcs12
RELEASE_STORE_PASSWORD=PASSWD
RELEASE_KEY_PASSWORD=PASSWD
RELEASE_KEY_ALIAS=ALIAS
~~~~

## Create Firebase Authentication file

To build with firebase support (all version but the ones for Fdroid) you need to get an
authentication file for firebase services offered by google. Therefore please follow
https://firebase.google.com/docs/android/setup to create your own project. You need to
register for the packages com.foobnix.pdf.info and com.foobnix.pdf.reader.a1. This way
you will get a google-services.json file that you have to place in the app folder of
the repository.

For this project only Analytics is used, so a spakling plan is all you need.

## Librera Build on MuPdf

~~~~
cd Builder
./link_to_mupdf_x.x.x.sh (Change the paths to mupdf and jniLibs folders)
cd ..
./gradlew assembleLibrera
~~~~

## Building for F-Droid for Android

If you wish to build for F-Droid (e.g. not using google services, Internet) you can run the build with

~~~~
cd Builder
./link_to_mupdf_x.x.x.sh
cd ..
./gradlew assembleFdroid
~~~~

F-Droid build does also not need a **google-services.json**

## What's new

See [CHANGELOG.md](CHANGELOG.md) for all changes.

Full release notes: https://github.com/foobnix/LibreraReader/releases

[Unreleased APK Direct Download](http://beta.librera.mobi)

## Android versions

| App Version | Librera PRO  | Librera F-Droid | Release Date |
|-------------|--------------|-----------------|--------------|
| [9.5.7](https://github.com/foobnix/LibreraReader/releases/tag/9.5.7) | 7.0 (API 24) | 7.0 (API 24)    | 2026-09-02 |
| [9.4.21](https://github.com/foobnix/LibreraReader/releases/tag/9.4.21) | 7.0 (API 24) | 7.0 (API 24)    | 2026-07-21 |
| [9.4.8](https://github.com/foobnix/LibreraReader/releases/tag/9.4.8) | 7.0 (API 24) | 7.0 (API 24)    | 2026-05-21 |
| [9.4.5](https://github.com/foobnix/LibreraReader/releases/tag/9.4.5) | 7.0 (API 24) | 7.0 (API 24)    | 2026-05-19 |
| [9.3.75](https://github.com/foobnix/LibreraReader/releases/tag/9.3.75) | 7.0 (API 24) | 7.0 (API 24)    | 2026-04-24 |
| [9.3.63](https://github.com/foobnix/LibreraReader/releases/tag/9.3.63) | 7.0 (API 24) | 7.0 (API 24)    | 2026-03-02 |
| [9.3.55](https://github.com/foobnix/LibreraReader/releases/tag/9.3.55) | 7.0 (API 24) | 7.0 (API 24)    | 2026-01-29 |
| [9.3.35](https://github.com/foobnix/LibreraReader/releases/tag/9.3.35) | 7.0 (API 24) | 7.0 (API 24)    | 2026-01-23 |
| [9.3.19](https://github.com/foobnix/LibreraReader/releases/tag/9.3.19) | 7.0 (API 24) | 7.0 (API 24)    | 2026-01-20 |
| [9.3.10](https://github.com/foobnix/LibreraReader/releases/tag/9.3.10) | 7.0 (API 24) | 7.0 (API 24)    | 2026-01-15 |
| [9.3.1](https://github.com/foobnix/LibreraReader/releases/tag/9.3.1) | 7.0 (API 24) | 7.0 (API 24)    | 2026-01-12 |
| [9.2.40](https://github.com/foobnix/LibreraReader/releases/tag/9.2.40) | 7.0 (API 24) | 7.0 (API 24)    | 2026-01-08 |
| [9.2.31](https://github.com/foobnix/LibreraReader/releases/tag/9.2.31) | 7.0 (API 24) | 7.0 (API 24)    | 2026-01-02 |
| [9.2.21](https://github.com/foobnix/LibreraReader/releases/tag/9.2.21) | 7.0 (API 24) | 7.0 (API 24)    | 2025-12-30 |
| [9.2.4](https://github.com/foobnix/LibreraReader/releases/tag/9.2.4) | 7.0 (API 24) | 7.0 (API 24)    | 2025-12-25 |
| [9.1.35](https://github.com/foobnix/LibreraReader/releases/tag/9.1.35) | 7.0 (API 24) | 5.0 (API 21)    | 2025-12-08 |
| [9.1.29](https://github.com/foobnix/LibreraReader/releases/tag/9.1.29) | 7.0 (API 24) | 5.0 (API 21)    | 2025-12-01 |
| [9.1.14](https://github.com/foobnix/LibreraReader/releases/tag/9.1.14) | 7.0 (API 24) | 4.1 (API 16)    | 2025-11-27 |
| [9.1.7](https://github.com/foobnix/LibreraReader/releases/tag/9.1.7) | 7.0 (API 24) | 4.1 (API 16)    | 2025-11-25 |
| [9.0.5](https://github.com/foobnix/LibreraReader/releases/tag/9.0.5) | 7.0 (API 24) | 4.1 (API 16)    | 2025-07-10 |
| [9.0.1](https://github.com/foobnix/LibreraReader/releases/tag/9.0.1) | 7.0 (API 24) | 4.1 (API 16)    | 2025-07-09 |
| [8.9.182](https://github.com/foobnix/LibreraReader/releases/tag/8.9.182) | 5.0 (API 21) | 4.1 (API 16)    | 2024-11-06 |
| [8.9.181](https://github.com/foobnix/LibreraReader/releases/tag/8.9.181) | 5.0 (API 21) | 4.1 (API 16)    | 2024-10-24 |
| [8.9.175](https://github.com/foobnix/LibreraReader/releases/tag/8.9.175) | 5.0 (API 21) | 4.1 (API 16)    | 2024-05-13 |
| [8.9.170](https://github.com/foobnix/LibreraReader/releases/tag/8.9.170) | 5.0 (API 21) | 4.1 (API 16)    | 2024-04-05 |
| [8.9.161](https://github.com/foobnix/LibreraReader/releases/tag/8.9.161) | 5.0 (API 21) | 4.1 (API 16)    | 2024-03-12 |
| [8.9.158](https://github.com/foobnix/LibreraReader/releases/tag/8.9.158) | 5.0 (API 21) | 4.1 (API 16)    | 2024-01-05 |
| [8.9.147](https://github.com/foobnix/LibreraReader/releases/tag/8.9.147) | 5.0 (API 21) | 4.1 (API 16)    | 2023-12-29 |
| [8.9.133](https://github.com/foobnix/LibreraReader/releases/tag/8.9.133) | 5.0 (API 21) | 4.1 (API 16)    | 2023-12-21 |
| [8.9.126](https://github.com/foobnix/LibreraReader/releases/tag/8.9.126) | 5.0 (API 21) | 4.1 (API 16)    | 2023-12-18 |

## Librera depends on

| Library    | Version          | License              |
|------------|------------------|----------------------|
| MuPDF      | 1.28.5 (1.23.7)  | AGPL-3.0             |
| DjVuLibre  | 3.5.28           | GPL-2.0-or-later     |
| antiword   | 1.3.1            | GPL-2.0              |
| libmobi    | 0.12             | LGPL-3.0-or-later    |
| hqx        | 1.2              | LGPL-2.1-or-later    |
| LAME       | 3.100            | LGPL-2.0-or-later    |
| libwebp    | 1.3.2            | BSD-3-Clause         |

| Component                                 | License                       |
|-------------------------------------------|-------------------------------|
| EBookDroid (`org.ebookdroid`, `org.emdev`) | GPL-3.0 <sup>1</sup>          |
| wmf2svg (`net.arnx.wmf2svg`)               | Apache-2.0                    |
| PBKDF2 (`de.rtner`)                        | LGPL-2.1-or-later             |
| opendocument.java (`at.stefl`)             | not declared <sup>1</sup>     |
| DragLinearLayout (`com.jmedeisis`)         | Apache-2.0 <sup>1</sup>       |
| HSV colour picker (`com.buzzingandroid`)   | Apache-2.0 <sup>1</sup>       |
| android-lame JNI (`com.github.axet`)       | Apache-2.0 <sup>1</sup>       |

| Dependency                                  | Version        | License                     |
|---------------------------------------------|----------------|-----------------------------|
| androidx.appcompat:appcompat                | 1.7.1          | Apache-2.0                  |
| androidx.cardview:cardview                  | 1.0.0          | Apache-2.0                  |
| androidx.legacy:legacy-support-v4           | 1.0.0          | Apache-2.0                  |
| androidx.recyclerview:recyclerview          | 1.4.0          | Apache-2.0                  |
| androidx.work:work-runtime                  | 2.11.2         | Apache-2.0                  |
| org.greenrobot:eventbus                     | 3.3.1          | Apache-2.0                  |
| org.greenrobot:greendao                     | 3.3.0          | Apache-2.0                  |
| org.greenrobot:greendao-api                 | 3.3.0          | Apache-2.0                  |
| org.jsoup:jsoup                             | 1.22.2         | MIT                         |
| com.github.albfernandez:juniversalchardet   | 2.5.0          | MPL-1.1 / GPL-3.0 / LGPL-3.0 |
| com.squareup.okhttp3:okhttp                 | 3.12.6         | Apache-2.0                  |
| io.github.rburgst:okhttp-digest             | 3.1.1          | Apache-2.0                  |
| com.squareup.okio:okio                      | 1.17.6         | Apache-2.0                  |
| com.github.joniles:rtfparserkit             | 1.16.0         | Apache-2.0                  |
| org.zwobble.mammoth:mammoth                 | 1.5.0          | BSD-2-Clause                |
| javax.xml.stream:stax-api                   | 1.0-2          | CDDL-1.0 / GPL-2.0          |
| net.lingala.zip4j:zip4j                     | 2.11.6         | Apache-2.0                  |
| com.github.bumptech.glide:glide             | 4.16.0         | BSD-2-Clause and Apache-2.0 |
| commons-logging:commons-logging-api         | 1.1            | Apache-2.0                  |
| com.google.guava:guava                      | 33.6.0-android | Apache-2.0                  |
| org.commonmark:commonmark                   | 0.29.0         | BSD-2-Clause                |
| org.commonmark:commonmark-ext-gfm-tables    | 0.29.0         | BSD-2-Clause                |
| com.github.junrar:junrar                       | 7.6.0                    | UnRar License                    |

Librera is distributed under the GPL v3

## License

See the [LICENSE](LICENSE.txt) file for license rights and limitations (GPL v.3).
