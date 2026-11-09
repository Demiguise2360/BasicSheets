namespace BasicSheets.Shared.Services;

/// <summary>
/// Saves/shares a generated file on native hosts (MAUI). On the web host this interface is
/// not registered, so callers fall back to a browser download instead.
/// </summary>
public interface IShareService
{
    Task ShareImageAsync(byte[] data, string fileName, string title);

    /// <summary>Writes the bytes to a file and hands it to the OS share sheet (any file type).</summary>
    Task ShareFileAsync(byte[] data, string fileName, string title);
}
