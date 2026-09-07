Add-Type -AssemblyName System.Drawing

$size = 512
$bmp = New-Object System.Drawing.Bitmap($size,$size,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)

function New-RoundPath([float]$x,[float]$y,[float]$w,[float]$h,[float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r*2
    $p.AddArc($x,$y,$d,$d,180,90)
    $p.AddArc($x+$w-$d,$y,$d,$d,270,90)
    $p.AddArc($x+$w-$d,$y+$h-$d,$d,$d,0,90)
    $p.AddArc($x,$y+$h-$d,$d,$d,90,90)
    $p.CloseFigure()
    return $p
}

# ---- 微信风格：绿色渐变圆底 + 白色聊天气泡 ----
$pad = 26
$tile = New-RoundPath 0 0 $size $size 120
$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.RectangleF(0,0,$size,$size)),
    [System.Drawing.Color]::FromArgb(255,38,198,99),
    [System.Drawing.Color]::FromArgb(255,7,160,65),
    45)
$g.FillPath($grad,$tile)

# ---- 白色聊天气泡（圆角矩形 + 下方小尾巴）----
$white = [System.Drawing.Brushes]::White
$bx = 122; $by = 118; $bw = 268; $bh = 224; $br = 46
$bubble = New-RoundPath $bx $by $bw $bh $br
$g.FillPath($white,$bubble)

# 尾巴（倒三角，接在气泡左下）
$tp = @()
$tp += , (New-Object System.Drawing.PointF -ArgumentList 158,320)
$tp += , (New-Object System.Drawing.PointF -ArgumentList 226,320)
$tp += , (New-Object System.Drawing.PointF -ArgumentList 158,406)
$tp = [System.Drawing.PointF[]]$tp
$g.FillPolygon($white, $tp)

# ---- 气泡内的绿色消息线条 ----
$line = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,38,175,98))
function FillBar([float]$x,[float]$y,[float]$w,[float]$h) {
    $p = New-RoundPath $x $y $w $h ([Math]::Min(10,$h/2))
    $g.FillPath($line,$p)
}
FillBar 172 168 148 22
FillBar 172 216 104 22
FillBar 172 264 126 22

# ---- 保存为 PNG（输出到 Android 与 Windows 共用资源）----
$out = "F:\Software-Development\OrangeGO\windows\OrangeGO.Windows\Assets\logo.png"
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Output "saved: $out"