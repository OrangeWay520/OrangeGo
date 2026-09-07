$ErrorActionPreference = "Stop"
$src = "F:\Software-Development\OrangeGO\windows\OrangeGO.Windows\Assets\emoji"
$repo = Get-ChildItem "F:\Software-Development" -Directory | ForEach-Object { Get-ChildItem $_.FullName -Directory -ErrorAction SilentlyContinue } | Where-Object { $_.Name -eq "fluentui-emoji-main" } | Select-Object -First 1
$repoAssets = Join-Path $repo.FullName "assets"

# Extract ALL panel emojis (BMP symbols + astral)
$cs = [System.IO.File]::ReadAllText("F:\Software-Development\OrangeGO\windows\OrangeGO.Windows\MainWindow.xaml.cs", [System.Text.Encoding]::UTF8)
$start = $cs.IndexOf("EmojiCategories")
if ($start -lt 0) { throw "EmojiCategories not found" }
$endRel = $cs.IndexOf("};", $start)
if ($endRel -lt 0) { throw "region end not found" }
$region = $cs.Substring($start, $endRel - $start)

$emojiSet = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($m in [regex]::Matches($region, '"([^"]*)"')) {
    $e = $m.Groups[1].Value
    if ($e.Length -eq 0) { continue }
    $isEmoji = $false
    $i = 0
    while ($i -lt $e.Length) {
        $cp = [char]::ConvertToUtf32($e, $i)
        if ([char]::IsSurrogatePair($e, $i)) { $i += 2 } else { $i += 1 }
        if (($cp -ge 0x2190 -and $cp -le 0x2BFF) -or $cp -ge 0x1F000 -or $cp -eq 0xA9 -or $cp -eq 0xAE -or $cp -eq 0x203C -or $cp -eq 0x2049) { $isEmoji = $true; break }
    }
    if ($isEmoji) { [void]$emojiSet.Add($e) }
}
Write-Host ("Panel distinct emoji (BMP+astral): " + $emojiSet.Count)

function ToHex([string]$ee) {
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $ee.Length;) {
        $cp = [char]::ConvertToUtf32($ee, $i)
        if ([char]::IsSurrogatePair($ee, $i)) { $i += 2 } else { $i += 1 }
        if ($cp -eq 0xFE0F) { continue }
        if ($sb.Length -gt 0) { [void]$sb.Append('-') }
        [void]$sb.Append($cp.ToString('x'))
    }
    return $sb.ToString()
}

function StripFe0fKey([string]$key) {
    $parts = @($key.Split('-') | Where-Object { $_ -ne "fe0f" })
    return ($parts -join '-')
}

# Build official unicode -> color svg map.
# FIX 1: read metadata.json as UTF-8 explicitly (PS5.1 Get-Content misreads as GBK and breaks JSON)
# FIX 2: skin-tone-split emojis have no top-level Color dir; fallback to Default\Color\*_color_default.svg
$map = @{}
$jsonFail = 0
foreach ($dir in Get-ChildItem $repoAssets -Directory) {
    $meta = Join-Path $dir.FullName "metadata.json"
    if (-not (Test-Path $meta)) { continue }
    $j = $null
    try {
        $raw = [System.IO.File]::ReadAllText($meta, [System.Text.Encoding]::UTF8)
        $j = $raw | ConvertFrom-Json
    } catch { $jsonFail++; continue }
    if ($null -eq $j -or [string]::IsNullOrEmpty($j.unicode)) { continue }
    $rawKey = (($j.unicode -replace '\s+', '-').ToLower())
    $cand = $null
    $colorSub = Join-Path $dir.FullName "Color"
    if (Test-Path $colorSub) {
        $cand = Get-ChildItem (Join-Path $colorSub '*_color.svg') -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if (-not $cand) {
        $defColor = Join-Path $dir.FullName "Default\Color"
        if (Test-Path $defColor) {
            # skin-tone files are named like victory_hand_color_default.svg
            $cand = Get-ChildItem (Join-Path $defColor '*_color*.svg') -ErrorAction SilentlyContinue | Select-Object -First 1
        }
    }
    if (-not $cand) { continue }
    foreach ($k in @($rawKey, (StripFe0fKey $rawKey))) {
        if (-not $map.ContainsKey($k)) { $map[$k] = $cand.FullName }
    }
}
Write-Host ("Official map entries: " + $map.Count + "  (jsonFail=" + $jsonFail + ")")

# Copy missing emoji SVGs
$copied = 0
$stillMissing = @()
foreach ($e in $emojiSet) {
    $hex = ToHex $e
    $has = (Test-Path (Join-Path $src ($hex + ".png"))) -or (Test-Path (Join-Path $src ($hex + "_color.svg"))) -or (Test-Path (Join-Path $src ($hex + "_flag.svg"))) -or (Test-Path (Join-Path $src ($hex + "_noto.png")))
    if ($has) { continue }
    if ($map.ContainsKey($hex)) {
        Copy-Item -Path $map[$hex] -Destination (Join-Path $src ($hex + "_color.svg")) -Force
        $copied++
    } else {
        $stillMissing += $hex
    }
}
Write-Host ("Copied new SVGs: " + $copied)
Write-Host ("Still missing: " + $stillMissing.Count + " -> " + ($stillMissing -join ", "))