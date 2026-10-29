using BasicSheets.Shared.Services;

namespace BasicSheets.Services;

public class NativeFilePickerService : IFilePickerService
{
    public async Task<PickedFile?> PickPdfAsync()
    {
        var options = new PickOptions
        {
            PickerTitle = "PDF, Bild oder Text auswählen",
            FileTypes = new FilePickerFileType(new Dictionary<DevicePlatform, IEnumerable<string>>
            {
                { DevicePlatform.Android, new[] { "application/pdf", "image/*", "text/*" } },
                { DevicePlatform.WinUI, new[] { ".pdf", ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
                                                ".cho", ".chopro", ".chordpro", ".crd", ".pro", ".txt" } },
                { DevicePlatform.iOS, new[] { "com.adobe.pdf", "public.image", "public.text", "public.plain-text" } },
                { DevicePlatform.macOS, new[] { "com.adobe.pdf", "public.image", "public.text", "public.plain-text" } },
            })
        };

        var result = await FilePicker.Default.PickAsync(options);
        if (result == null) return null;

        var info = new FileInfo(result.FullPath);
        return new PickedFile
        {
            FileName = result.FileName,
            FullPath = result.FullPath,
            Size = info.Length
        };
    }
}
