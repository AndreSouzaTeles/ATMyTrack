$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$source = [Drawing.Bitmap]::new((Join-Path $root 'branding/0.21.0/source.png'))
try {
    # Export the extracted disc inside the same circular viewport used by Android.
    # This excludes alpha flecks beyond the rim and gives every size equal margins.
    foreach ($size in @(48,72,96,144,192,512,1024)) {
        $bitmap = [Drawing.Bitmap]::new($size,$size,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        $path = [Drawing.Drawing2D.GraphicsPath]::new()
        try {
            $graphics.Clear([Drawing.Color]::Transparent)
            $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $margin = [single]($size * 0.01)
            $rect = [Drawing.RectangleF]::new($margin,$margin,[single]($size-2*$margin),[single]($size-2*$margin))
            $path.AddEllipse($rect)
            $graphics.SetClip($path)
            $graphics.DrawImage($source,$rect,[Drawing.RectangleF]::new(25,15,1194,1182),[Drawing.GraphicsUnit]::Pixel)
            $bitmap.Save((Join-Path $root "branding/0.21.0/icon-$size.png"),[Drawing.Imaging.ImageFormat]::Png)
        } finally { $path.Dispose();$graphics.Dispose();$bitmap.Dispose() }
    }
} finally { $source.Dispose() }
Copy-Item (Join-Path $root 'branding/0.21.0/icon-1024.png') (Join-Path $root 'app/src/main/res/drawable-nodpi/brand_art.png')
Copy-Item (Join-Path $root 'branding/0.21.0/icon-512.png') (Join-Path $root 'web/dist/assets/logo.png')
$densities = @{mdpi=48;hdpi=72;xhdpi=96;xxhdpi=144;xxxhdpi=192}
foreach ($density in $densities.Keys) {
    foreach ($name in @('ic_launcher','ic_launcher_round')) {
        Copy-Item (Join-Path $root "branding/0.21.0/icon-$($densities[$density]).png") (Join-Path $root "app/src/main/res/mipmap-$density/$name.png")
    }
}
