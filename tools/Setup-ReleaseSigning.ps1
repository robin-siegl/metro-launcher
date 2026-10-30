[CmdletBinding()]
param(
    [string]$Repository = 'robin-siegl/Metro-Launcher',
    [string]$KeystorePath = '',
    [string]$KeyAlias = 'metro-launcher'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw 'Repository must have the form owner/name.'
}
if ([string]::IsNullOrWhiteSpace($KeyAlias)) { throw 'KeyAlias cannot be empty.' }

$gh = Get-Command gh -CommandType Application -ErrorAction SilentlyContinue
if (-not $gh) { throw 'Install GitHub CLI from https://cli.github.com/ and run gh auth login first.' }
& $gh.Source auth status --active --hostname github.com
if ($LASTEXITCODE -ne 0) { throw 'Run gh auth login, then run this script again.' }

$keytool = Get-Command keytool -CommandType Application -ErrorAction SilentlyContinue
$keytoolPath = if ($keytool) { $keytool.Source } else { '' }
if (-not $keytoolPath) {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\keytool.exe' }
    if ($env:ProgramFiles) { $candidates += Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\keytool.exe' }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $keytoolPath = $candidate; break }
    }
}
if (-not $keytoolPath) { throw 'keytool was not found. Install Android Studio or a JDK, or add its bin folder to PATH.' }

if (-not $KeystorePath) {
    $dataRoot = [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
    if (-not $dataRoot) { throw 'Pass -KeystorePath with an absolute path outside the repository.' }
    $KeystorePath = Join-Path $dataRoot 'Metro-Launcher\release-signing\metro-release.jks'
}
$KeystorePath = [IO.Path]::GetFullPath($KeystorePath)
$existing = Test-Path -LiteralPath $KeystorePath -PathType Leaf

function Read-PlainPassword([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
}

function Set-RepositorySecret([string]$Name, [string]$Value) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $gh.Source
    $startInfo.Arguments = "secret set $Name --repo $Repository"
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        [void]$process.Start()
        $writer = [IO.StreamWriter]::new($process.StandardInput.BaseStream, [Text.UTF8Encoding]::new($false), 4096, $true)
        try { $writer.Write($Value); $writer.Flush() }
        finally { $writer.Dispose() }
        $process.StandardInput.Close()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Could not set $Name. Check your repository access." }
    }
    finally { $process.Dispose() }
}

$storePassword = Read-PlainPassword 'Keystore password (at least 6 characters)'
if ([string]::IsNullOrEmpty($storePassword) -or $storePassword.Length -lt 6) { throw 'The password must have at least 6 characters.' }
if ($existing) {
    $keyPassword = Read-PlainPassword 'Key password (leave empty to use the keystore password)'
    if (-not $keyPassword) { $keyPassword = $storePassword }
} else {
    $confirmation = Read-PlainPassword 'Confirm keystore password'
    if ($confirmation -cne $storePassword) { throw 'Passwords do not match.' }
    $keyPassword = $storePassword
    $confirmation = $null
}

$previousStoreVariable = $env:METRO_SIGN_SETUP_STORE_PASSWORD
$previousKeyVariable = $env:METRO_SIGN_SETUP_KEY_PASSWORD
try {
    $env:METRO_SIGN_SETUP_STORE_PASSWORD = $storePassword
    $env:METRO_SIGN_SETUP_KEY_PASSWORD = $keyPassword
    if (-not $existing) {
        [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($KeystorePath))
        & $keytoolPath -genkeypair -keystore $KeystorePath -storetype JKS -alias $KeyAlias `
            -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Metro Launcher' `
            -storepass:env METRO_SIGN_SETUP_STORE_PASSWORD -keypass:env METRO_SIGN_SETUP_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Creating the signing key failed.' }
    }
    & $keytoolPath -list -keystore $KeystorePath -alias $KeyAlias -storepass:env METRO_SIGN_SETUP_STORE_PASSWORD | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'The keystore password or alias is incorrect.' }

    $keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystorePath))
    Set-RepositorySecret 'ANDROID_KEYSTORE_BASE64' $keystoreBase64
    Set-RepositorySecret 'ANDROID_KEYSTORE_PASSWORD' $storePassword
    Set-RepositorySecret 'ANDROID_KEY_ALIAS' $KeyAlias
    Set-RepositorySecret 'ANDROID_KEY_PASSWORD' $keyPassword
    Write-Host "Signing secrets configured for $Repository."
    Write-Host "Keep a private backup of $KeystorePath and its password."
    Write-Host "Run the workflow at https://github.com/$Repository/actions/workflows/production.yml"
}
finally {
    $env:METRO_SIGN_SETUP_STORE_PASSWORD = $previousStoreVariable
    $env:METRO_SIGN_SETUP_KEY_PASSWORD = $previousKeyVariable
    $storePassword = $null
    $keyPassword = $null
    $keystoreBase64 = $null
}
