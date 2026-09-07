$ErrorActionPreference = "Stop"
$src = "F:\Software-Development\图片资源\fluentui-emoji-main\assets"
$dst = "f:\Software-Development\OrangeGO\windows\OrangeGO.Windows\Assets\emoji"
New-Item -ItemType Directory -Force -Path $dst | Out-Null

# 读取面板源码里的所有 emoji（EmojiCategories 起的字符串字面量中，含代理对的即 emoji）
$cs = [System.IO.File]::ReadAllText("f:\Software-Development\OrangeGO\windows\OrangeGO.Windows\MainWindow.xaml.cs", [System.Text.Encoding]::UTF8)
$start = $cs.IndexOf("EmojiCategories")
if ($start -lt 0) { throw "未找到 EmojiCategories" }
$region = $cs.Substring($start)
$emojiSet = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($m in [regex]::Matches($region, '"([^"]*)"')) {
    $e = $m.Groups[1].Value
    if ($e.Length -eq 0) { continue }
    $hasSur = $false
    for ($i = 0; $i -lt $e.Length; $i++) {
        $c = [int][char]$e[$i]
        if ($c -ge 0xD800 -and $c -le 0xDBFF) { $hasSur = $true; break }
    }
    if ($hasSur) { [void]$emojiSet.Add($e) }
}
Write-Host ("Panel distinct emoji: " + $emojiSet.Count)

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

# 建 unicode -> color svg 路径映射（官方仓库：assets/<name>/Color/<name>_color.svg）
$map = @{}
foreach ($dir in Get-ChildItem $src -Directory) {
    $meta = Join-Path $dir.FullName "metadata.json"
    if (-not (Test-Path $meta)) { continue }
    try {
        $j = Get-Content $meta -Raw | ConvertFrom-Json
    } catch { continue }
    if ([string]::IsNullOrEmpty($j.unicode)) { continue }
    $hexKey = (($j.unicode -replace '\s+', '-').ToLower())
    $colorSub = Join-Path $dir.FullName "Color"
    $cand = $null
    if (Test-Path $colorSub) {
        $cand = Get-ChildItem (Join-Path $colorSub '*_color.svg') -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if (-not $cand) { continue }
    if (-not $map.ContainsKey($hexKey)) { $map[$hexKey] = $cand.FullName }
}
Write-Host ("Official unicode entries: " + $map.Count)

# 拷贝面板用到的非国旗 emoji 官方 color svg 到 Assets
$copied = 0
$noOfficial = @()
foreach ($e in $emojiSet) {
    $hex = ToHex $e
    if ($hex -like '1f1??-1f1??') { continue }   # 国旗跳过（官方仓库无国旗）
    if ($map.ContainsKey($hex)) {
        Copy-Item -Path $map[$hex] -Destination (Join-Path $dst ($hex + "_color.svg")) -Force
        $copied++
    } else {
        $noOfficial += $hex
    }
}
Write-Host ("Copied official Fluent color svg: " + $copied)
Write-Host ("No official match (keep existing): " + $noOfficial.Count)
if ($noOfficial.Count) { Write-Host ("  " + ($noOfficial -join ", ")) }