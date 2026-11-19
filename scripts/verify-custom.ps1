# Verify all customization hooks are intact (run after merging upstream)
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\verify-custom.ps1
# Kept ASCII-only to avoid Windows PowerShell 5.1 GBK/UTF-8 issues.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$javaDir = Join-Path $root "app\src\main\java\org\b3log\siyuan"

$checks = @(
    @{ File = "RemoteConfig.java";  Pattern = "class RemoteConfig";                          Desc = "RemoteConfig class exists" }
    @{ File = "RemoteKernel.java";  Pattern = "class RemoteKernel";                          Desc = "RemoteKernel class exists" }
    @{ File = "RemoteConfig.java";  Pattern = "ENABLED = true";                              Desc = "remote master switch is ON" }
    @{ File = "RemoteSettings.java";Pattern = "class RemoteSettings";                        Desc = "RemoteSettings (user input address) exists" }
    @{ File = "RemoteSettings.java";Pattern = "normalize";                                   Desc = "address normalize logic exists" }
    @{ File = "RemoteAuth.java";    Pattern = "class RemoteAuth";                            Desc = "RemoteAuth login class exists" }
    @{ File = "RemoteAuth.java";    Pattern = "urlInput";                                     Desc = "login dialog has server address field" }
    @{ File = "RemoteAuth.java";    Pattern = "getSharedPreferences";                        Desc = "settings stored locally" }
    @{ File = "MainActivity.java";  Pattern = "SYNUIAN-CUSTOM-BEGIN (remote kernel hook)";   Desc = "bootKernel hook exists" }
    @{ File = "MainActivity.java";  Pattern = "RemoteKernel.start";                          Desc = "remote start delegate call exists" }
    @{ File = "MainActivity.java";  Pattern = "refreshServerAddress";                        Desc = "dynamic address refresh exists" }
    @{ File = "MainActivity.java";  Pattern = "SYNUIAN-CUSTOM-BEGIN (remote readiness hook)";Desc = "readiness hook exists" }
    @{ File = "JSAndroid.java";     Pattern = "RemoteSettings.origin()";                     Desc = "JSAndroid address migrated" }
    @{ File = "Utils.java";         Pattern = "RemoteSettings.origin()";                     Desc = "Utils address migrated" }
)

$fail = 0
Write-Output "===== Customization integrity check ====="
foreach ($c in $checks) {
    $path = Join-Path $javaDir $c.File
    if (-not (Test-Path $path)) {
        Write-Output "[FAIL] $($c.File) missing - $($c.Desc)"; $fail++; continue
    }
    $content = Get-Content $path -Raw -Encoding UTF8
    if ($content -match [regex]::Escape($c.Pattern)) {
        Write-Output "[ OK ] $($c.Desc)"
    } else {
        Write-Output "[FAIL] $($c.File) lacks: $($c.Pattern) - $($c.Desc)"; $fail++
    }
}

$leftover = Select-String -Path "$javaDir\*.java" -Pattern 'http://127\.0\.0\.1:6806' | Where-Object { $_.Path -notmatch 'Utils.java' }
if ($leftover) {
    Write-Output "[WARN] hardcoded 127.0.0.1:6806 still present:"
    $leftover | ForEach-Object { "   $($_.Filename):$($_.LineNumber)" }
} else {
    Write-Output "[ OK ] no leftover hardcoded addresses"
}

# 确认没有把授权码硬编码进源码
$hardcoded = Select-String -Path "$javaDir\RemoteConfig.java" -Pattern 'AUTH_CODE\s*=|\w{6,}546883298|zmf546883298'
if ($hardcoded) {
    Write-Output "[FAIL] hardcoded auth code still present in RemoteConfig.java"; $fail++
} else {
    Write-Output "[ OK ] no hardcoded auth code in source"
}

Write-Output "======================================"
if ($fail -eq 0) { Write-Output "All checks passed. Safe to rebuild."; exit 0 }
else { Write-Output "$fail check(s) failed. Customization may be lost after merge; see CUSTOMIZATION.md."; exit 1 }
