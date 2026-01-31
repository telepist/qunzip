# Extract the compressed folder (archive) icon from Windows with full color depth
# Uses PrivateExtractIcons for proper 32-bit RGBA color

Add-Type -AssemblyName System.Drawing

$code = @"
using System;
using System.Runtime.InteropServices;
using System.Drawing;
using System.IO;

public class HighQualityIconExtractor {
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int PrivateExtractIcons(
        string lpszFile, int nIconIndex, int cxIcon, int cyIcon,
        IntPtr[] phicon, int[] piconid, int nIcons, int flags);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool DestroyIcon(IntPtr hIcon);

    // Extract icon with specific size
    public static IntPtr ExtractIcon(string file, int index, int size) {
        IntPtr[] icons = new IntPtr[1];
        int[] ids = new int[1];
        int count = PrivateExtractIcons(file, index, size, size, icons, ids, 1, 0);
        if (count > 0 && icons[0] != IntPtr.Zero) {
            return icons[0];
        }
        return IntPtr.Zero;
    }

    // Create a proper multi-size ICO file
    public static void CreateMultiSizeIcon(string sourceFile, int iconIndex, string outputPath) {
        int[] sizes = { 256, 48, 32, 16 };

        using (var ms = new MemoryStream())
        using (var writer = new BinaryWriter(ms)) {
            // ICO header
            writer.Write((short)0);      // Reserved
            writer.Write((short)1);      // Type: 1 = ICO
            writer.Write((short)sizes.Length);  // Number of images

            int dataOffset = 6 + (sizes.Length * 16);  // Header + directory entries
            var imageData = new MemoryStream[sizes.Length];

            // First pass: extract all icons and calculate offsets
            for (int i = 0; i < sizes.Length; i++) {
                int size = sizes[i];
                IntPtr hIcon = ExtractIcon(sourceFile, iconIndex, size);

                if (hIcon == IntPtr.Zero) {
                    throw new Exception("Failed to extract icon at size " + size);
                }

                Icon icon = Icon.FromHandle(hIcon);
                imageData[i] = new MemoryStream();

                // Get the PNG data for this size
                Bitmap bmp = icon.ToBitmap();
                bmp.Save(imageData[i], System.Drawing.Imaging.ImageFormat.Png);
                bmp.Dispose();

                DestroyIcon(hIcon);
            }

            // Write directory entries
            for (int i = 0; i < sizes.Length; i++) {
                int size = sizes[i];
                byte widthByte = (byte)(size >= 256 ? 0 : size);
                byte heightByte = (byte)(size >= 256 ? 0 : size);

                writer.Write(widthByte);   // Width (0 = 256)
                writer.Write(heightByte);  // Height (0 = 256)
                writer.Write((byte)0);     // Color palette
                writer.Write((byte)0);     // Reserved
                writer.Write((short)1);    // Color planes
                writer.Write((short)32);   // Bits per pixel
                writer.Write((int)imageData[i].Length);  // Image size
                writer.Write(dataOffset);  // Offset to image data

                dataOffset += (int)imageData[i].Length;
            }

            // Write image data
            for (int i = 0; i < sizes.Length; i++) {
                writer.Write(imageData[i].ToArray());
                imageData[i].Dispose();
            }

            // Save to file
            File.WriteAllBytes(outputPath, ms.ToArray());
        }
    }
}
"@

Add-Type -TypeDefinition $code -ReferencedAssemblies System.Drawing

$zipfldrPath = "$env:SystemRoot\System32\zipfldr.dll"
$outputPath = Join-Path $PSScriptRoot "icon.ico"

Write-Host "Extracting high-quality archive icon..."
Write-Host "Source: $zipfldrPath"
Write-Host "Output: $outputPath"

try {
    [HighQualityIconExtractor]::CreateMultiSizeIcon($zipfldrPath, 0, $outputPath)

    if (Test-Path $outputPath) {
        $size = (Get-Item $outputPath).Length
        Write-Host "Icon created successfully!" -ForegroundColor Green
        Write-Host "File size: $size bytes"
        Write-Host "Includes sizes: 256x256, 48x48, 32x32, 16x16 (32-bit color)"
    }
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
}
