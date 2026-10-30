# Verification for 0.1.0

- Official Android SDK 36, Build Tools 35.0.0, AGP 8.13.2, Gradle 8.13, Java 17.
- `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`: passed.
- Core layout/search checks: 3,110,467 assertions; 3,600 randomized edits.
- API 28 emulator, `AndroidJUnitRunner`: **OK (4 tests)**.
- Device cases: actual HOME candidate registration; customized folder backup round trip; invalid/duplicate backup rejection and safe photo-path handling; left/right touch swipes between Start and apps.
- Debug APK signature: verified with official `apksigner`, APK signature scheme v2.
- APK SHA-256: `363cdaf0a066f177fde5e0a0845c9f02d9abdb0c611a64210435f22ec66f76ce`.
- Package: `at.robin.metro`, version `0.1.0` (1), Android 8.0+ (minimum API 26), target API 36.

Android 17 / API 37, physical-device performance, all customization combinations and pixel-level comparison with a Lumia still require device testing. The recreated launcher covers Start and the app list; Android retains its system UI.

## Production workflow

The repository release setup was validated separately using a temporary signing key kept outside the repository:

- `testReleaseUnitTest`, `lintRelease`, `assembleRelease`: passed.
- Release APK signature: verified by `apksigner`.
- Release manifest: version `0.1.0`, code `1001`, no `application-debuggable` flag.
- All three GitHub Actions workflow files: passed `actionlint`.
- Production preflight: accepted normal/prerelease versions, rejected invalid input and missing secrets.
- Windows setup script: PowerShell parser passed; secure-password conversion and exact UTF-8 secret transport were tested with a local mock GitHub CLI.

The temporary validation key is not a production signing key and is not committed or distributed. Real production runs use the owner's repository signing secrets, configured as described in [docs/RELEASE.md](docs/RELEASE.md).
