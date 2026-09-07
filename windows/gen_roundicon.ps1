param([double]$radiusFrac = 0.235)
Add-Type -AssemblyName System.Drawing
$bak = "F:\Software-Development\OrangeGO\windows\OrangeGO.Windows\Assets\logo.roundbak.png"
$src = "F:\Software-Development\OrangeGO\windows\OrangeGO.Windows\Assets\logo.png"
$img = [System.Drawing.Image]::FromFile($bak)
$size = $img.Width
$pf = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
$bmp = New-Object System.Drawing.Bitmap -ArgumentList @($size, $size, $pf)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)
$r = [int]($size * $radiusFrac)
$d = $r * 2
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddArc(0, 0, $d, $d, 180, 90)
$path.AddArc($size - $d, 0, $d, $d, 270, 90)
$path.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
$path.AddArc(0, $size - $d, $d, $d, 90, 90)
$path.CloseFigure()
$g.SetClip($path)
$g.DrawImage($img, 0, 0, $size, $size)
$g.ResetClip()
$bmp.Save($src, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $path.Dispose(); $img.Dispose(); $bmp.Dispose()
Write-Host "圆角图标已生成 radius=$r size=$size"