namespace BasicSheets.Shared.Services;

public interface IUpdateService
{
    bool UpdateAvailable { get; }
    string? NewVersion { get; }
    string? Changelog { get; }
    bool IsChecking { get; }
    bool IsDownloading { get; }
    int DownloadPercent { get; }
    string? ErrorMessage { get; }
    Task CheckForUpdateAsync();
    Task DownloadAndInstallAsync();
}

public interface IUpdateServiceNotify
{
    event Action? StateChanged;
}
