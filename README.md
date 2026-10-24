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

### On the maintainer's machine (Ubuntu)

Two helper scripts do steps 1 to 3. They live outside the repo, in
`.agents/skills/dev-build-apk/scripts/`: `setup_env.sh` runs once (it installs the packages, the
NDK, the JDK, `~/.gradle/gradle.properties`, a release key and MuPDF; safe to run again), and
`build_apk.sh` builds. From the repo folder:

```bash
bash .agents/skills/dev-build-apk/scripts/setup_env.sh                         # once
bash .agents/skills/dev-build-apk/scripts/build_apk.sh fdroid release --debug-key
```

The SDK comes from `sdk.dir` in `local.properties`. When the checkout is on a Windows or network
disk (`/mnt/c`, `/mnt/d`...), MuPDF is built in `~/librera-native` and Gradle runs in a copy in
`~/librera-build`, both much faster; the APKs are copied back and each one is checked for
`libMuPDF.so`. A good build ends with:

```
[build] [OK]  Librera Fdroid-9.6.44-mupdf1.28.5-arm64.apk  32M  libMuPDF.so x1
[build] APKs: /mnt/d/0.Projects/00/LibreraReader/app/build/outputs/apk/fdroid/release
```

The same scripts run in WSL on Windows (`wsl -e bash /mnt/c/<repo>/.agents/skills/dev-build-apk/scripts/build_apk.sh ...`;
from Git Bash put `MSYS_NO_PATHCONV=1` before `wsl`). The debug key (`~/.android/debug.keystore`,
made by `--debug-key` when missing) is per machine: a phone updates only over APKs signed with the
key it already has, so copy the old machine's key over when moving the build, or uninstall first.

A failure prints a line with `[ERR]`. Arguments: `[flavor] [debug|release]`, then any of:

| Option | Does |
|--------|------|
| `--debug-key` | signs with the debug key, as above (installs over a build made with the same key) |
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
| `com.foobnix.annotation` | stylus rules; marks found again after an EPUB re-layout; the marks file; Markdown export; color names |
| `com.foobnix.study` | study chapters (long parts split), the skim sent at opt-in, preview and check answers, chapter cards, time per chapter, the study file, quotes found in the chapter, the outline (Markdown and mind map) |

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
- **Marks (PDF)**: open a PDF and tap the edit button in the top bar. Highlight, underline,
  strike, note, draw, erase and undo (undo also brings back an erased mark). Close the book: it
  does not ask to save (the PDF is not changed). Reopen it: the marks are there. Check them in book
  and scroll mode, zoomed, cropped, and in a page curl.
- **Marks (EPUB, FB2, MOBI...)**: the edit button shows the same toolbar without the pen. Mark a
  few passages, then change the font size: the marks move with their words. Try two pages per
  screen on a tablet.
- **Notes**: the Note tool: select text, type a note, Save: a highlight with a small round sign.
  Tap any mark: its card (quote, page, color, kind) lets you add or edit a note, change the color
  or delete it. A stylus on an EPUB highlights the text it passes over.
- **Save marks into PDF**: in a PDF, toolbar ⋮ > Save marks into PDF: it asks, then writes them.
  Open the PDF in another app (Xodo, Zotero): the highlights are there, notes as comments. In
  Librera they are now ordinary PDF annotations (tap one: its comment and Delete).
- **Export notes**: book menu (reader, or long-press in the library) > Export notes (Markdown).
  Share it to Obsidian; then Save to a folder (pick the vault folder once): `<Title>.md` with the
  marks under chapter headings, notes, bookmarks and AI passages. Export again: the file is replaced.
- **Marks over WebDAV**: mark the same book on two devices before syncing: after a sync both
  devices show both sets. Erase one on a device: it goes on the other too.
- **AI colors**: ✦ > Highlight key passages on a non-fiction chapter: the first time it asks what
  you want from the book. Marks come in red, blue, purple, orange, green and grey, never yellow.
  ✦ > Colors shows what each means. Messages of the pen toolbar now appear above it, not over it.
- **AI ideas** (one EPUB and one PDF chapter, ideally a two-column PDF): ✦ > Highlight key
  passages > This chapter. The result names the ideas marked and any "not located". Pages should
  rarely be empty: key sentences filled, must-read paragraphs with a colored bar in the left
  margin, also where a paragraph runs onto the next page (and into the next column). ✦ > Ideas of
  this chapter: every idea, indented under the one it supports; does it miss anything you think
  matters? Tap an idea: the book goes to it. Tap a sentence or a bar: its idea, Explain (an answer
  streams in), All ideas. ✦ > Paragraph bars / Key sentences hide each layer. Whole book, then
  Cancel: the chapters done stay.
- **Study** (a non-fiction book with a table of contents): ✦ > Study this book. It asks your goal
  if the book has none, skims the book (about a minute), then opens the Study panel: thesis,
  goal, "Read · Skim · Skip" counts, time vs the whole book, and every chapter with ● ◐ ○, its
  time and state. Do the labels make sense for your goal? Tap a ● to change it; tap a chapter for
  its questions and actions. Page into a new chapter: a card at the bottom shows its label, time,
  reason and questions (Read: Prepare this chapter; Skim: Summary, Prepare this chapter; Skip:
  Summary, Next chapter; a prepared chapter shows Ideas instead of Prepare); the next page
  turn takes it away, and it never comes again for that chapter. Page out of a Read or Skim
  chapter: "Done: <chapter>" comes first, with Answer and Later. A jump (TOC, search) shows only
  the card of where you land. A book split in Parts gets a card per chapter inside them.
  Answer: type (or dictate with the keyboard's mic) an answer per question and, if you like,
  "Anything else you remember?"; close and reopen: what you typed is still there. Check: the
  feedback comes in as it is written, then ✓ / ◐ / ✗ per answer with the sentence of the book and
  "p. N": a tap goes there (in an EPUB also after a font change). "Added to the map: N ideas".
  With no answers the button is "Add to map". In the panel, a checked chapter has Answers and
  Rebuild branch; a skipped one Add to map. Panel > Map: the outline as a mind map (zoom, pan,
  tap a circle to fold, tap a text to go to its page) or as Outline; in night mode it is dark.
  Type what you still remember at the bottom, Check: it names what you left out (only the
  outline is sent, not the book). Export notes (Markdown) starts with "## Outline".
  Close the book, set the phone date 3 days ahead (or wait), open it: "Welcome back" > Look back
  first opens the map with the recall box. ⋮ > Stop studying hides everything; ✦ > Study this
  book brings it back without a new skim. A second device shows the same panel, answers and map
  after a sync, and a card seen on one is not shown on the other.
- **Stylus** (a phone or tablet with an active pen): open a PDF without opening the toolbar and
  write with the pen: it draws at once, thinner or bolder with pressure, while a finger still turns
  pages. Rest your palm while writing: nothing happens. Set "Stylus draws with" to Highlighter
  (reader menu > advanced preferences) and drag over a line of text: the text under it is selected
  as you go and highlighted when you lift (over a picture: nothing, with a message). Hold the side button: the other tool. Tap a mark with the eraser
  end (if the pen has one). Undo after a pressure stroke removes all of it. ⋮ > Save marks into PDF,
  reopen in another PDF reader, check the marks are there.
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
- **WebDAV tab speed**: open a folder, wait a second, then open one of its subfolders: it shows at
  once. Swipe the app away, open it again on the WebDAV tab: Books shows at once, then refreshes.
  Tap a folder never seen while a pull to refresh runs: it opens without waiting for the refresh.
- **Flat covers**: update both devices and sync each once: the server's `Books/.covers/` holds files
  only, no folders, and every cover still shows (none is made again). Move a book to another
  folder on the server from a computer: it keeps its cover.
- **Back in a book**: back closes the book and shows the library, also for a book opened from a
  file manager or another app. A long press of back still shows the close menu.
- **AI**: tap ✦ in the top bar (book and scroll mode: the same place, by the cog), then AI settings:
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
- **Background fills the screen** (on by default; reader Preferences, under the day/night colors):
  pick a texture, then a plain color, in book and scroll mode, day and night, a PDF and an EPUB.
  The paper runs unbroken round a PDF page (the strips above and below it), between pages while
  scrolling, beside a zoomed-out page, under the status line of scroll mode (while the menu is
  hidden), under the clock and battery, round the camera and over the navigation-bar margin. The
  texture is one picture across the screen, not one copy per page. The clock and battery stay
  readable (dark on light paper). A page curl shows the texture too. Turn the option off: the old
  look comes back, with the "Foreground" color in the color dialog again.
- **Page turn** (use the release APK; debug builds make animations stutter): in book mode, open
  the page flipping dialog or the advanced preferences and pick each "Page turn" style:
  - Slide, Cover, Page curl: turn forward and back by tap, volume key and swipe. A slow, short
    drag of the curl should fall back; a flick should complete it.
  - Page curl on a page with a TTS or AI highlight: the highlight curls with the page.
  - A right-to-left book (manga): every style is mirrored.
  - "Vibrate lightly when a page turns": a light tick for your own turns, none during autoplay.
- **Eye mode** (release APK; a device with a front camera): open a book, open the menu, tap the eye
  next to the headphones. The first time a card explains it, then Android asks for the camera
  (deny once: a note offers the app settings). "Eye mode on"; an eye shows in the status bar (book
  mode: bottom, beside the battery; scroll mode: top bar, beside the clock) and the green camera
  dot appears. Long-press the eye button: its settings. Turn on "Show what the camera sees": a
  corner panel shows the camera picture (mirrored), dots on your face, the state and the numbers,
  a strip of the last 30 s (green sees you, orange away, grey can't see) and Eye mode's last
  action ("paused auto-scroll / flipping · 3s ago", or why it did nothing). Eye mode learns your
  reading pose in its first 3 s ("learning your reading pose…"), then shows how far you are off
  it ("off pose: head … eyes …"). Looking away = clearly off that pose (head about 20°, or the
  eyes up from the page or to a side), or out of the picture. Change position in bed, then touch
  the page: that is your reading pose now.
  - Lit room: look at the page: open eye. Look away or close your eyes for 2 s: closed eye. Start
    auto-scroll (scroll mode) or page flipping (book mode), look away: it stops; look back: it
    goes on. Stay away 30 s: the screen dims; look back or touch: it comes back.
  - Read with no touch for longer than "keep screen on" minutes: the screen stays on while you
    look.
  - In bed at night with the night theme: what does the corner panel say (face found? "dark"?
    luma)? The icon should be the slashed eye ("can't see you") when it cannot tell, and then
    auto-scroll must not stop.
  - "Also pause read-aloud" on: start read-aloud, look away: it pauses; look back: it goes on.
  - Long blink (M2, "Long blink turns the page", on): close your eyes for about a second and open
    them: the next page (book mode) or screen (scroll mode), with a tick. The corner panel shows
    "eyes open N% N%" (CLOSED while closed) and the last closure ("0.9 s LONG"). Normal blinking,
    reading the bottom lines, looking down at your lap, resting the eyes 3 s: no turn. Not while
    read-aloud plays or a dialog is open. Record a trace with a few long blinks and a few looks
    down, in a lit room and in bed.
  - Gaze (M3): the first time a gaze feature applies (book mode: "Gaze page turn"; scroll mode:
    "Gaze auto-scroll speed"), Eye mode offers to calibrate: a dark screen, a dot in 7 places
    (about 12 s), follow it with your eyes, then "Now close your eyes until you feel a tick"
    (about 3 s); back or Cancel stops it. "Calibrated: about ±N% of the screen" and whether your
    closed eyes were learned (long blink then uses your own limit: reading the bottom lines must
    not count as closed eyes, a real long blink must).
    The settings show "Calibrate this orientation (calibrated / not calibrated)"; turn the phone:
    that orientation is offered once too. With "Show what the camera sees", an orange ring shows
    where Eye mode thinks you look, with two lines (book mode: top zone and last lines; scroll
    mode: the quarters). Does the ring follow you down the page?
  - Book mode: read to the last lines, rest there a moment, look up to the top: next page (tick).
    A glance at the clock or the title soon after a page came, or reading back up slowly: no
    turn. Tap the eye in the status bar: one dot in the middle, "Gaze re-centred".
  - Scroll mode with auto-scroll on: keep reading near the top: it waits ("TOP ×0.0" by the
    lines); in the middle it goes at your speed; near the bottom it speeds up bit by bit (up to
    twice), and slows back when you are in the middle again. Your set speed stays the same.
  - Turn the phone: the dots stay on your face. Switch book ↔ scroll mode: Eye mode stays on.
    Close the book and open it again: Eye mode is off.
  - Battery: note the level, read 30 min with Eye mode on (corner panel off), compare.
  - Statistics tab after an Eye mode session: a "Looking" tile; book info: "Looking at the page".
  - "Record face traces" on, read a few minutes, "Share the last trace": a .jsonl of numbers, no
    pictures. Send it if something behaved wrong.

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
