using System.Text.Json;
using BasicSheets.Shared.Services;

#if ANDROID
using Android.Content;
#endif

namespace BasicSheets.Services;

public class UpdateService : IUpdateService, IUpdateServiceNotify
{
    // Use the netlify.app host for update checks/downloads: it always has a valid TLS
    // certificate. The custom domain (basicsheets.desgin.ch) currently has no provisioned
    // Let's Encrypt cert, so Android's HttpClient rejects it. These URLs are internal
    // (never shown to users) — the branded domain isn't needed here.
    private const string VersionUrl = "https://basicsheets.netlify.app/assets/version.json";
    private const string ApkUrl = "https://basicsheets.netlify.app/assets/basicsheets.apk";

    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromMinutes(5) };

    public bool UpdateAvailable { get; private set; }
    public string? NewVersion { get; private set; }
    public string? Changelog { get; private set; }
    public bool IsChecking { get; private set; }
    public bool IsDownloading { get; private set; }
    public int DownloadPercent { get; private set; }
    public string? ErrorMessage { get; private set; }

    public event Action? StateChanged;

    public async Task CheckForUpdateAsync()
    {
        if (IsChecking) return;
        IsChecking = true;
        ErrorMessage = null;
        try
        {
            var json = await _http.GetStringAsync(VersionUrl);
            var info = JsonSerializer.Deserialize<RemoteVersion>(json,
                new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
            if (info == null) return;

            if (int.TryParse(AppInfo.Current.BuildString, out var currentCode)
                && info.VersionCode > currentCode)
            {
                UpdateAvailable = true;
                NewVersion = info.Version;
                Changelog = info.Changelog;
            }
        }
        catch (Exception ex)
        {
            ErrorMessage = $"Update-Check fehlgeschlagen: {ex.Message}";
            Console.WriteLine($"[UpdateService] {ErrorMessage}");
        }
        finally
        {
            IsChecking = false;
            StateChanged?.Invoke();
        }
    }

    public async Task DownloadAndInstallAsync()
    {
        if (IsDownloading) return;
        IsDownloading = true;
        DownloadPercent = 0;
        ErrorMessage = null;
        StateChanged?.Invoke();

        try
        {
            var apkPath = Path.Combine(FileSystem.CacheDirectory, "basicsheets-update.apk");

            using var response = await _http.GetAsync(ApkUrl, HttpCompletionOption.ResponseHeadersRead);
            response.EnsureSuccessStatusCode();

            var totalBytes = response.Content.Headers.ContentLength ?? -1;
            await using var contentStream = await response.Content.ReadAsStreamAsync();
            await using var fileStream = new FileStream(apkPath, FileMode.Create, FileAccess.Write, FileShare.None);

            var buffer = new byte[81920];
            long downloaded = 0;
            int bytesRead;

            while ((bytesRead = await contentStream.ReadAsync(buffer)) > 0)
            {
                await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead));
                downloaded += bytesRead;
                if (totalBytes > 0)
                {
                    DownloadPercent = (int)(downloaded * 100 / totalBytes);
                    StateChanged?.Invoke();
                }
            }

            DownloadPercent = 100;
            StateChanged?.Invoke();

            InstallApk(apkPath);
        }
        catch (Exception ex)
        {
            ErrorMessage = $"Download fehlgeschlagen: {ex.Message}";
            Console.WriteLine($"[UpdateService] {ErrorMessage}");
        }
        finally
        {
            IsDownloading = false;
            StateChanged?.Invoke();
        }
    }

    private static void InstallApk(string apkPath)
    {
#if ANDROID
        var context = Platform.AppContext;
        var authority = context.PackageName + ".updateprovider";
        var apkFile = new Java.IO.File(apkPath);
        var apkUri = AndroidX.Core.Content.FileProvider.GetUriForFile(context, authority, apkFile);

        var intent = new Intent(Intent.ActionView);
        intent.SetDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.AddFlags(ActivityFlags.NewTask);
        intent.AddFlags(ActivityFlags.GrantReadUriPermission);
        context.StartActivity(intent);
#endif
    }

    private class RemoteVersion
    {
        public string Version { get; set; } = "";
        public int VersionCode { get; set; }
        public string Changelog { get; set; } = "";
    }
}
