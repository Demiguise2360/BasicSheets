using BasicSheets.Shared.Services;

namespace BasicSheets.Services;

/// <summary>
/// Native share: write the bytes to a cache file and hand it to the OS share sheet
/// (Share Sheet on iOS/macOS, the Android share chooser, the Windows share dialog).
/// </summary>
public class ShareService : IShareService
{
    public Task ShareImageAsync(byte[] data, string fileName, string title)
        => ShareFileAsync(data, fileName, title);

    public async Task ShareFileAsync(byte[] data, string fileName, string title)
    {
        var path = Path.Combine(FileSystem.Current.CacheDirectory, fileName);
        await File.WriteAllBytesAsync(path, data);

        await Share.Default.RequestAsync(new ShareFileRequest
        {
            Title = title,
            File  = new ShareFile(path)
        });
    }
}
