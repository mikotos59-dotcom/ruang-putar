# ambil_bahan.ps1 — dipanggil buat_installer.bat
# Mengunduh sekali saja (disimpan di folder "bahan"):
#   - JDK 17 portabel (Temurin)      -> bahan\jdk
#   - libmpv-2.dll (mesin suara mpv) -> sumber\windows\libmpv-2.dll
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$dasar = Split-Path -Parent $MyInvocation.MyCommand.Path
$bahan = Join-Path $dasar "bahan"
New-Item -ItemType Directory -Force -Path $bahan | Out-Null

# ---------------------------------------------------------------- JDK 17
$jdk = Join-Path $bahan "jdk"
if (-not (Test-Path (Join-Path $jdk "bin\java.exe"))) {
    Write-Host "Mengunduh JDK 17 (± 190 MB, sekali saja)..."
    $zip = Join-Path $bahan "jdk.zip"
    Invoke-WebRequest -UseBasicParsing -Uri "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $zip
    $tmp = Join-Path $bahan "jdk-tmp"
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive -Path $zip -DestinationPath $tmp
    $isi = Get-ChildItem $tmp | Select-Object -First 1
    if (Test-Path $jdk) { Remove-Item -Recurse -Force $jdk }
    Move-Item $isi.FullName $jdk
    Remove-Item -Recurse -Force $tmp, $zip
}
Write-Host "JDK: $jdk"

# ---------------------------------------------------------------- libmpv
$dll = Join-Path $dasar "sumber\windows\libmpv-2.dll"
if (-not (Test-Path $dll)) {
    Write-Host "Mengunduh libmpv (mesin pemutar suara, sekali saja)..."
    New-Item -ItemType Directory -Force -Path (Split-Path $dll) | Out-Null
    $url = $null
    foreach ($repo in @("shinchiro/mpv-winbuild-cmake", "zhongfly/mpv-winbuild")) {
        try {
            $r = Invoke-RestMethod -UseBasicParsing -Uri "https://api.github.com/repos/$repo/releases/latest" -Headers @{ "User-Agent" = "RuangPutar" }
            $aset = $r.assets | Where-Object { $_.name -match '^mpv-dev-x86_64-\d{8}-git-[0-9a-f]+\.7z$' } | Select-Object -First 1
            if ($aset) { $url = $aset.browser_download_url; break }
        } catch { }
    }
    if (-not $url) { throw "libmpv tidak ditemukan di GitHub" }
    $arsip = Join-Path $bahan "mpv.7z"
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $arsip
    $7z = @("$env:ProgramFiles\7-Zip\7z.exe", "${env:ProgramFiles(x86)}\7-Zip\7z.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $7z) { throw "7-Zip belum terpasang (https://www.7-zip.org) — dibutuhkan untuk membuka libmpv .7z" }
    $tmp = Join-Path $bahan "mpv-tmp"
    & $7z x -y "-o$tmp" $arsip | Out-Null
    $f = Get-ChildItem -Recurse -Path $tmp -Filter "libmpv-2.dll" | Select-Object -First 1
    Copy-Item $f.FullName $dll
    Remove-Item -Recurse -Force $tmp, $arsip
}
Write-Host "libmpv: $dll"
