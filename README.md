# Metro Launcher

A native Android home launcher recreating the Windows Phone 8 / 8.1 Start and app-list experience. Java 17, Android Views and Canvas; Android 8.0 or newer. The application has no third-party runtime libraries.

## Build from GitHub Actions

Open [Build Production APK](https://github.com/robin-siegl/Metro-Launcher/actions/workflows/production.yml), click **Run workflow**, choose the version and run it. Download the `Metro-Launcher-<version>-release` artifact when it succeeds, extract the APK and install it.

Production signing requires one setup: [generate a key and add four GitHub secrets](docs/RELEASE.md#one-time-signing-setup). Run the `keytool` command, copy the key as Base64 in PowerShell, and paste the four values into **Settings → Secrets and variables → Actions**. No repository clone or GitHub CLI is required. Keep the key and passwords for future app updates. The PowerShell setup script remains available as an optional shortcut.

For an immediate debug APK with no signing setup, run [Android checks and APK](https://github.com/robin-siegl/Metro-Launcher/actions/workflows/android.yml) and download `metro-launcher-debug`.

## Start here

1. Clone this repository and open its root folder in Android Studio.
2. Use the bundled JDK 17 or newer supported JDK, and install **Android SDK Platform 36** and **Build Tools 35.0.0** from SDK Manager. The Gradle toolchain is pinned to AGP 8.13.2 and Gradle 8.13.
3. Let Gradle sync, then run the `app` configuration on your phone. You can also build an APK with the command below.
4. On first launch, tap **set as Home app** and choose Metro in Android's system prompt. Or choose **explore first**, then set it later in **start+theme → Home app**.

The initial debug version passed the Android SDK 36 build, Android lint, JUnit checks and all four API 28 emulator tests. Physical-device appearance, performance and fidelity against an original Lumia still need a phone pass.

### Windows / PowerShell

From the project folder:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

If Java is missing from your terminal, use the Android Studio terminal or set your JDK path, for example:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

Android Studio's bundled JDK must be compatible with Gradle 8.13; JDK 17 or 21 works. The command-line build also needs the Android SDK configured through Android Studio's generated `local.properties` or `ANDROID_HOME`.

### macOS / Linux

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK appears at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and open it to install, or run:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A debug build signs itself. You do not need a release keystore or signing secrets to try it.

Locally built APKs or separate CI runs may use a different debug signing key from the supplied APK. If Android requires uninstalling before installing one of those builds, export your layout first and import it afterwards.

## What is implemented

| Area | Behaviour |
| --- | --- |
| Actual launcher | HOME/DEFAULT intent registration, single-task activity, first-run setup, Android Home-role request, Home-button handling |
| Start | Flat, square, tightly spaced tiles; no dashboard header; vertically scrolling layout; swipe left or use the footer arrow for all apps |
| Tile sizes | Small 1×1, medium 2×2 and wide 4×2 on the small-tile grid |
| Arrangement | Hold to edit; drag to a grid position; automatic collision handling; intentional gaps persist; edge scrolling while dragging |
| Editing | Unpin control at upper right, resize control at lower right, bottom customization bar, tap a gap or done to finish |
| Folders | In 8.1 mode, hold a dragged app tile over another tile's centre for 650 ms; a second haptic and outline signal folder creation |
| Folder contents | Expand in Start, rename, rearrange/resize children, unpin them, or move them back out; folders use the selected column count |
| App list | Real installed launcher activities, alphabetical groups, letter jump grid, case/diacritic-insensitive search, long-press pin menu |
| App actions | Open exact launcher activity, system app-info page and Android's uninstall confirmation |
| Theme | All 20 WP-style accent colours, light/dark background, two/three medium-tile columns, solid-tile 8 mode and photo/folder 8.1 mode |
| Photo | Android file picker, drag/pinch crop, photo visible only through eligible tiles, scroll parallax; no photo-library permission |
| Tile customization | Rename, individual colour, photo/solid background, live content on/off, explicit size choice |
| Icons and font | Metro symbols for common app categories, original app icons for other apps, optional monochrome icons, optional user-supplied TTF/OTF |
| Live information | Actual date and clock; optional active-notification counts; optional notification text on wide tiles |
| Persistence | Atomic private JSON state, retained positions and theme, export/import with validation and confirmation |
| Accessibility | Focusable tiles, spoken labels, resize/unpin/customize accessibility actions, keyboard tile movement while editing |
| Package changes | Installed, removed and updated apps refresh the list; unavailable pinned apps remain removable |

## Controls

- Swipe **left** on Start for apps; swipe **right** to return.
- In the app list, tap the magnifier to search. Tap a letter box for the alphabet jump grid.
- Hold an app and choose **pin to start**.
- Hold a tile to edit it. Drag to move it. Tap its lower-right arrow to cycle sizes or the upper-right × to unpin it.
- To make a folder, drag an app tile to the centre of another tile and pause until the outline/haptic changes, then release. This is available in 8.1 mode.
- Tap a folder to expand it. Tap the folder name to rename. Hold a child tile for arranging and the **move out** action.
- The **customize** bar offers label, colour, transparency and live-tile options.
- Find **start+theme** alphabetically in the app list, pin it, or use **•••** beneath the Start tiles.
- Back closes the current overlay/edit/folder/search, returns from apps to Start, then scrolls Start to the top. Home returns to Start.

## Live tiles and privacy

Clock and date need no additional permission. Android notification access is optional and must be enabled explicitly in system settings. Counts represent active Android notifications, which can differ from an app's unread-message total. Notification titles and text only appear if you enable **Notification text on wide tiles**. Secret notifications do not supply preview text. Notification data stays in memory and is neither saved nor transmitted.

Photos are copied and downsampled into private app storage. Font files are copied into private storage. Layout exports contain tile/app identifiers, labels and theme choices, so store them as you would any description of your installed apps. Photo and font bytes are not included in JSON exports; choose those separately on a new device.

No Internet, contacts, SMS, call-log, accessibility-service, broad storage or all-packages permission is requested. Automatic Android cloud backup is disabled. Package discovery is limited to launchable activities.

## Fidelity and Android boundaries

The implementation follows the Windows Phone 8.1 Start interaction model. It is an initial recreation, and has not had a device-based pixel or gesture comparison against an original Lumia.

- Android retains the status/navigation bars, notification shade, lock screen, Recents and cross-app transitions. A normal Home app cannot replace these with Windows Phone system UI.
- Android apps retain their own interfaces and icons. Common app categories receive original Metro-style symbol drawings in this launcher.
- Segoe WP is not bundled. The default is Microsoft's open-source Selawik Light, a Segoe UI replacement; the custom-font option can use a font you have permission to use.
- Original Windows Phone service-backed live data, People Hub mosaics and app-specific WP live-tile providers are not available. This version uses real clock/date data and Android notifications.
- Work profiles, Android app widgets, pinned Android app shortcuts, contact tiles and lock-screen customization are outside this version's scope.
- Switching to the 8 experience makes tiles solid and disables creating new folders by dragging. Existing folders remain accessible, retaining their contents.

## Verification

The authoring environment executed the real platform-independent placement and search code: **3,110,467 assertions**, including **3,600 randomized move/resize/reflow/reinsert operations**. These checks assert that no tiles disappear, duplicate or overlap, columns stay valid, deliberate gaps remain, copied folder data stays independent and German/Unicode search works.

```sh
tools/check-core.sh
```

The helper also parses every Java source for syntax errors. The full Android build was subsequently run with the official SDK 36 and Build Tools 35.0.0: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` all passed. The resulting APK's v2 signature was verified with Android's `apksigner`.

With a local SDK:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

All **four instrumented tests passed on an API 28 emulator**. They cover Home registration, customized folder backup round trips, malformed/duplicate backup rejection, safe background-file handling and real touch-input swipes between Start and apps. Start was also inspected visually in the emulator; a tile clipping bug found during that pass was corrected. Android 17 / API 37 and a physical Lumia comparison were not tested here.

### GitHub Actions

Pushes to `main`/`master` and pull requests run **Android checks and APK**, which shows separate **Unit tests**, **Android lint** and **Build APK** checks. You can run it manually too. After the checks pass, download **metro-launcher-debug**. Debug builds do not need signing secrets.

**Build Production APK** is a manual workflow with signing validation, release unit tests, release lint and a signed APK build. It verifies the signature and supplies a checksum. See [production release setup](docs/RELEASE.md).

**Android device tests** is a separate manual workflow for API 28 and 35 emulators.

### First device pass

1. Install; select Metro as Home; press Home from another app and after a reboot.
2. Pin an app, move it through occupied cells, cycle all sizes, unpin, and confirm the layout survives a restart.
3. Enable more tiles; repeat in a folder; switch back to two columns.
4. Test left/right swipes, vertical scrolling, letter jumps, search, installation/uninstallation updates and Back.
5. Try light/dark, all accents, a cropped photo, an individual solid tile and a custom font.
6. Enable and revoke notification access; post/remove a notification and check counts. Enable text previews only if wanted.
7. Export, change the arrangement, import the backup, and verify folders and theme.

## Project structure

```text
app/src/main/java/at/robin/metro/
  MainActivity.java         Home lifecycle, navigation and actions
  model/                    Android-independent tiles, layout and search
  data/                     State/backup validation, app catalog and notifications
  ui/                       Tile board, drawing, pager, app list, settings and crop
app/src/test/               Deterministic layout/search checks
app/src/androidTest/        Android integration and pointer-swipe tests
tools/                     SDK-free core checks and Java syntax parser
.github/workflows/         Android build/lint/tests and manual emulator tests
```

Reference behaviour: [Microsoft's original Start background walkthrough](https://blogs.windows.com/devices/2014/04/29/deep-dive-windows-phone-8-1-start-screen-backgrounds/), [Live Folders announcement](https://blogs.windows.com/windowsexperience/2014/07/30/windows-phone-8-1-update-brings-cortana-to-new-markets-new-features/), and [alphabet jump/search explanation](https://blogs.windows.com/windowsexperience/2011/07/08/navigation-improvements-coming-in-mango/).

Independent project; Windows Phone and Lumia are Microsoft product names. App source is MIT licensed. See `THIRD_PARTY_NOTICES.md` for the bundled Gradle wrapper.
