# Build a production APK

Open [Build Production APK](https://github.com/robin-siegl/Metro-Launcher/actions/workflows/production.yml), click **Run workflow**, choose `main`, set the version if needed, and click **Run workflow** again. When the run succeeds, download the `Metro-Launcher-<version>-release` artifact. Extract it and install the `.apk` on your phone. The adjacent `.sha256` file records its checksum.

The workflow runs release unit tests and Android lint in separate jobs, signs the release APK, verifies its signature, and uploads the APK. It uses a version code of `1000 + the production workflow run number`, so subsequent runs increase the code automatically. GitHub retains the artifact for 30 days.

## One-time signing setup

Android uses the signing key to identify an app's updates. Configure this repo's signing secrets once, then keep using the same key for every release. The GitHub connection used to upload this project cannot set Actions secrets, so this setup is performed from your own machine or GitHub's settings page.

### Windows / PowerShell

Install [GitHub CLI](https://cli.github.com/) and Android Studio or a JDK, then run this from a clone of this repo:

```powershell
gh auth login
powershell -ExecutionPolicy Bypass -File .\tools\Setup-ReleaseSigning.ps1
```

The script finds `keytool` in your PATH, `JAVA_HOME`, or the standard Android Studio JBR folder. It asks for a password, creates a JKS outside the repository under your local application-data folder, and sends four secrets to GitHub through standard input. Passwords and the key are not printed or committed. Retain a private backup of the keystore and its password.

To use an existing key instead:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\Setup-ReleaseSigning.ps1 `
  -KeystorePath 'C:\keys\metro-release.jks' -KeyAlias 'metro-launcher'
```

The script reuses the file if it exists and asks for its passwords. It never overwrites an existing keystore.

### GitHub settings

You can alternatively add these under **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the entire release JKS/keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias of the signing key, for example `metro-launcher` |
| `ANDROID_KEY_PASSWORD` | Password of that key; it may equal the keystore password |

To copy an existing keystore as base64 on Windows:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\keys\metro-release.jks')) | Set-Clipboard
```

The workflow checks for missing secrets before running the Android jobs and names any missing entries. It checks the keystore password/alias before compiling. A missing or incorrect key fails the run; the production workflow does not substitute a debug signing key.

## Updating an existing installation

Install future production APKs over the previous version when they share the same release key. The earlier prototype APK was signed with a different debug key. Export your layout before uninstalling that prototype, then install the first production APK and import your layout. Photo and font files are selected separately.

## Local signed build

Set `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` in your local environment, then run:

```powershell
.\gradlew.bat assembleRelease -PrequireReleaseSigning=true `
  -PreleaseVersionName=0.1.0 -PreleaseVersionCode=1001
```

The signed APK is at `app/build/outputs/apk/release/app-release.apk`. Without signing variables, a local `assembleRelease` produces an unsigned APK; the production workflow always requires signing. Keep the release key, passwords and generated artifacts out of Git.
