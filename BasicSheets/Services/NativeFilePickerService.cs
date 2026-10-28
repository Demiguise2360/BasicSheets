using BasicSheets.Shared.Services;

namespace BasicSheets.Services;

public class NativeFilePickerService : IFilePickerService
{
    public async Task<PickedFile?> PickPdfAsync()
    {
        var options = new PickOptions
        {
            PickerTitle = "PDF auswählen",
            FileTypes = new FilePickerFileType(new Dictionary<DevicePlatform, IEnumerable<string>>
            {
                { DevicePlatform.Android, new[] { "application/pdf" } },
                { DevicePlatform.WinUI, new[] { ".pdf" } },
                { DevicePlatform.iOS, new[] { "com.adobe.pdf" } },
                { DevicePlatform.macOS, new[] { "com.adobe.pdf" } },
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
