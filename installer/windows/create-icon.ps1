# PowerShell script to create a simple Qunzip icon
# Requires .NET/PowerShell 5.0+

Add-Type -AssemblyName System.Drawing

# Create a 256x256 bitmap
$size = 256
$bitmap = New-Object System.Drawing.Bitmap($size, $size)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)

# Enable anti-aliasing for smoother appearance
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias

# Create gradient background (blue)
$gradientBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Rectangle(0, 0, $size, $size)),
    [System.Drawing.Color]::FromArgb(70, 130, 200),  # Light blue
    [System.Drawing.Color]::FromArgb(30, 80, 140),   # Dark blue
    45  # Gradient angle
)
$graphics.FillRectangle($gradientBrush, 0, 0, $size, $size)

# Add rounded rectangle border
$borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 255, 255), 8)
$graphics.DrawRectangle($borderPen, 12, 12, $size - 24, $size - 24)

# Draw "GZ" text in the center
$font = New-Object System.Drawing.Font("Arial", 96, [System.Drawing.FontStyle]::Bold)
$textBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$text = "GZ"

# Measure text size for centering
$textSize = $graphics.MeasureString($text, $font)
$x = ($size - $textSize.Width) / 2
$y = ($size - $textSize.Height) / 2

# Draw text with shadow effect
$shadowBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(0, 0, 0))
$graphics.DrawString($text, $font, $shadowBrush, $x + 4, $y + 4)
$graphics.DrawString($text, $font, $textBrush, $x, $y)

# Save as PNG first
$pngPath = Join-Path $PSScriptRoot "icon-temp.png"
$bitmap.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)

Write-Host "Created temporary PNG at: $pngPath"

# Cleanup
$graphics.Dispose()
$bitmap.Dispose()
$font.Dispose()
$textBrush.Dispose()
$shadowBrush.Dispose()
$borderPen.Dispose()
$gradientBrush.Dispose()

Write-Host ""
Write-Host "PNG created successfully!"
Write-Host ""
Write-Host "To convert to .ico file, you can use one of these methods:"
Write-Host "1. Online converter: https://convertio.co/png-ico/ (upload icon-temp.png)"
Write-Host "2. ImageMagick: magick convert icon-temp.png -define icon:auto-resize=256,48,32,16 icon.ico"
Write-Host "3. GIMP: Open icon-temp.png, Export As -> icon.ico"
Write-Host ""
Write-Host "After conversion, save as 'icon.ico' in the installer/windows directory"
Write-Host "Then you can delete icon-temp.png"
