param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$natluRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$natluBrand = Join-Path $natluRoot 'publishing\branding'
$natluRes = Join-Path $natluRoot 'mushaf_app\app\src\main\res'
$natluPlay = Join-Path $natluRoot 'mushaf_app\app\src\main\play\listings\ar\graphics'

# Mechanical platform exports from the approved artwork; no logo redrawing.
function Export-NatluPng {
    param([string]$Source, [string]$Destination, [int]$Width, [int]$Height, [switch]$Round)
    $natluImage = [System.Drawing.Image]::FromFile($Source)
    $natluBitmap = [System.Drawing.Bitmap]::new($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $natluGraphics = [System.Drawing.Graphics]::FromImage($natluBitmap)
    $natluAttributes = [System.Drawing.Imaging.ImageAttributes]::new()
    $natluClip = $null
    try {
        $natluGraphics.Clear([System.Drawing.Color]::Transparent)
        $natluGraphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $natluGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $natluGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $natluGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        # Mirror edge pixels so resizing opaque artwork does not introduce transparent seams.
        $natluAttributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
        if ($Round) {
            $natluClip = [System.Drawing.Drawing2D.GraphicsPath]::new()
            $natluClip.AddEllipse(0, 0, $Width, $Height)
            $natluGraphics.SetClip($natluClip)
        }
        $natluScale = [Math]::Max($Width / $natluImage.Width, $Height / $natluImage.Height)
        $natluSourceWidth = [single]($Width / $natluScale)
        $natluSourceHeight = [single]($Height / $natluScale)
        $natluGraphics.DrawImage(
            $natluImage, [System.Drawing.Rectangle]::new(0, 0, $Width, $Height),
            [single](($natluImage.Width - $natluSourceWidth) / 2),
            [single](($natluImage.Height - $natluSourceHeight) / 2),
            $natluSourceWidth, $natluSourceHeight,
            [System.Drawing.GraphicsUnit]::Pixel, $natluAttributes
        )
        [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($Destination)) | Out-Null
        $natluBitmap.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        if ($null -ne $natluClip) { $natluClip.Dispose() }
        $natluAttributes.Dispose()
        $natluGraphics.Dispose()
        $natluBitmap.Dispose()
        $natluImage.Dispose()
    }
}

$natluIcon = Join-Path $natluBrand 'approved-icon.png'
$natluForeground = Join-Path $natluBrand 'foreground-master.png'
$natluFeature = Join-Path $natluBrand 'feature-graphic-master.png'
Export-NatluPng $natluIcon (Join-Path $natluRoot 'publishing\play-store-icon-512.png') 512 512
Export-NatluPng $natluIcon (Join-Path $natluRes 'drawable-nodpi\app_icon.png') 512 512
Export-NatluPng $natluIcon (Join-Path $natluRoot 'docs\assets\app-icon.png') 512 512
Export-NatluPng $natluForeground (Join-Path $natluRes 'drawable-nodpi\ic_launcher_foreground_image.png') 432 432
foreach ($natluDensity in @{mdpi=48;hdpi=72;xhdpi=96;xxhdpi=144;xxxhdpi=192}.GetEnumerator()) {
    $natluDirectory = Join-Path $natluRes ('mipmap-' + $natluDensity.Key)
    Export-NatluPng $natluIcon (Join-Path $natluDirectory 'ic_launcher.png') $natluDensity.Value $natluDensity.Value
    Export-NatluPng $natluIcon (Join-Path $natluDirectory 'ic_launcher_round.png') $natluDensity.Value $natluDensity.Value -Round
}
Export-NatluPng $natluFeature (Join-Path $natluRoot 'publishing\feature-graphic-1024x500.png') 1024 500
Export-NatluPng $natluFeature (Join-Path $natluRoot 'docs\assets\natlu-hero.png') 1600 781
[System.IO.Directory]::CreateDirectory((Join-Path $natluPlay 'icon')) | Out-Null
[System.IO.Directory]::CreateDirectory((Join-Path $natluPlay 'feature-graphic')) | Out-Null
Copy-Item -LiteralPath (Join-Path $natluRoot 'publishing\play-store-icon-512.png') -Destination (Join-Path $natluPlay 'icon\1.png')
Copy-Item -LiteralPath (Join-Path $natluRoot 'publishing\feature-graphic-1024x500.png') -Destination (Join-Path $natluPlay 'feature-graphic\1.png')
foreach ($natluScreenshotSet in @(
    @{ Source='publishing\screenshots'; Target='phone-screenshots' },
    @{ Source='publishing\tablet-screenshots'; Target='tablet-screenshots' },
    @{ Source='publishing\tablet-screenshots'; Target='large-tablet-screenshots' }
)) {
    $natluScreenshots = @(Get-ChildItem -LiteralPath (Join-Path $natluRoot $natluScreenshotSet.Source) -Filter '*.png' | Sort-Object Name)
    if ($natluScreenshots.Count -gt 8) { throw 'Google Play allows at most eight screenshots per device type.' }
    $natluTarget = Join-Path $natluPlay $natluScreenshotSet.Target
    [System.IO.Directory]::CreateDirectory($natluTarget) | Out-Null
    for ($natluIndex=0; $natluIndex -lt $natluScreenshots.Count; $natluIndex++) {
        Copy-Item -LiteralPath $natluScreenshots[$natluIndex].FullName -Destination (Join-Path $natluTarget (($natluIndex+1).ToString() + '.png'))
    }
}
Write-Output 'Exported Natlu launcher, adaptive foreground, app, website, store graphics and screenshots.'
