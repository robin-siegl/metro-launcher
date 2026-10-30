# Build and publish a production APK

Open [Build Production APK](https://github.com/robin-siegl/Metro-Launcher/actions/workflows/production.yml), click **Run workflow**, choose `main`, enter a new version such as `0.1.0` or `0.1.1`, and click **Run workflow** again. When all jobs succeed, open [GitHub Releases](https://github.com/robin-siegl/Metro-Launcher/releases/latest), expand **Assets**, download the `.apk` and install it on your phone. The adjacent `.sha256` file records its checksum.

The workflow runs release unit tests and Android lint in separate jobs, signs the release APK, verifies its signature, and uploads the APK. A separate **Publish GitHub Release** job checks the checksum, creates a tag such as `v0.1.0` at the exact built commit, and publishes the APK and checksum with generated release notes. Only that job has permission to write releases. It uses the built-in GitHub token; no additional secrets are needed.

Release downloads are public, require no GitHub sign-in, and remain available until the release or assets are deleted. The latest stable download is always at [releases/latest](https://github.com/robin-siegl/Metro-Launcher/releases/latest). Actions also keeps the `Metro-Launcher-<version>-release` ZIP artifact for 30 days.

Production runs are restricted to `main`. Enter a new version for each release: existing version tags are rejected before building and published APKs are never overwritten. A version with a suffix such as `0.2.0-beta.1` is published as a prerelease and does not replace the latest stable download. Android's version code is `1000 + the production workflow run number`, so later builds increase the code automatically.

## One-time signing setup

Generate a key with `keytool`, copy it as Base64, and add four secrets in GitHub's settings. This is the same setup used by [PIP-OS](https://github.com/robin-siegl/pip-os/blob/main/docs/RELEASING.md). You only need a JDK (Android Studio includes one); no repository clone or GitHub CLI is required.

Android uses the signing key to identify app updates. **Back up the keystore and passwords securely, and keep using the same key for every Metro release.** If you already have a Metro release key, skip generation and use its file, alias and passwords below.

### 1. Generate the release key

Open PowerShell in a folder outside the Git repository where you want to keep your key, then run:

```powershell
keytool -genkeypair -v -storetype JKS -keystore metro-release.jks -alias metro-launcher -keyalg RSA -keysize 3072 -validity 10000
```

Choose a keystore password of at least six characters and answer the certificate prompts. When asked for the key password, press **Enter** to use the same password. The file is `metro-release.jks` and the alias is `metro-launcher`.

If PowerShell cannot find `keytool` and you have Android Studio in its standard Windows location, run this once in that PowerShell window, then retry the command above:

```powershell
$env:Path = 'C:\Program Files\Android\Android Studio\jbr\bin;' + $env:Path
```

### 2. Copy the key as Base64

In the same PowerShell folder:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\metro-release.jks))) | Set-Clipboard
```

This copies the whole key file as a single Base64 line to your clipboard.

### 3. Add the four GitHub secrets

Open this repository's [Actions secrets settings](https://github.com/robin-siegl/Metro-Launcher/settings/secrets/actions): **Settings → Secrets and variables → Actions**. Click **New repository secret** for each row, enter the exact name, paste its value, and click **Add secret**.

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Paste the clipboard contents from step 2 |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password you chose |
| `ANDROID_KEY_ALIAS` | `metro-launcher` |
| `ANDROID_KEY_PASSWORD` | The same password if you pressed Enter at the key-password prompt |

For an existing keystore, use its actual alias and passwords, and adjust the file path in step 2. Never commit the keystore, its Base64 contents or passwords.

### 4. Run the production build

Open [Build Production APK](https://github.com/robin-siegl/Metro-Launcher/actions/workflows/production.yml), click **Run workflow**, choose `main`, enter a new version and run it. When **Publish GitHub Release** succeeds, download the APK from the release's **Assets** or the download link in the run summary.

The four secrets stay configured for future builds; repeat only this step for later releases.

The workflow checks for missing secrets before running the Android jobs and names any missing entries. It checks the keystore password/alias before compiling. If a secret is missing, add it in the settings page above and rerun the workflow.

If tests or the build fail, no release is published. If publishing fails, the signed APK is still available in the run's **Artifacts**; fix the reported issue and rerun the failed publishing job. If GitHub reports that the version already exists, choose a new version for a new build. If it reports an Actions permission error, ensure repository policy allows the publishing job's `contents: write` permission.

### Optional: configure secrets with the PowerShell helper

If you prefer an automated setup, install [GitHub CLI](https://cli.github.com/) and Android Studio or a JDK, then run this from a clone of this repo:

```powershell
gh auth login
powershell -ExecutionPolicy Bypass -File .\tools\Setup-ReleaseSigning.ps1
```

The script finds `keytool`, asks for a password, creates a JKS outside the repository under your local application-data folder, and configures the four secrets. Passwords and the key are not printed or committed. Back up the keystore and password.

To use an existing key instead:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\Setup-ReleaseSigning.ps1 `
  -KeystorePath 'C:\keys\metro-release.jks' -KeyAlias 'metro-launcher'
```

The script reuses the file if it exists and asks for its passwords. It never overwrites an existing keystore.

## Updating an existing installation

Install future production APKs over the previous version when they share the same release key. The earlier prototype APK was signed with a different debug key. Export your layout before uninstalling that prototype, then install the first production APK and import your layout. Photo and font files are selected separately.

## Local signed build

Set `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` in your local environment, then run:

```powershell
.\gradlew.bat assembleRelease -PrequireReleaseSigning=true `
  -PreleaseVersionName=0.1.0 -PreleaseVersionCode=1001
```

The signed APK is at `app/build/outputs/apk/release/app-release.apk`. Without signing variables, a local `assembleRelease` produces an unsigned APK; the production workflow always requires signing. Keep the release key, passwords and generated artifacts out of Git.
