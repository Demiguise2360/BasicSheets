using BasicSheets.Shared.Models;
using SQLite;

namespace BasicSheets.Shared.Data;

/// <summary>
/// Owns the single SQLite connection for the whole app (registered as a DI singleton).
/// Creates all tables on first use and runs the one-time legacy-JSON import. All access
/// goes through <see cref="Locked{T}"/> so the connection is used serially.
/// </summary>
public class BasicSheetsDb
{
    private readonly object _gate = new();

    public SQLiteConnection Conn { get; }
    public string DataDir { get; }
    public string DbPath { get; }

    /// <summary>Directories searched (in order) for the old flat-JSON stores to import.</summary>
    public IReadOnlyList<string> LegacyDirs { get; }

    public BasicSheetsDb() : this(
        Environment.GetEnvironmentVariable("BASICSHEETS_DB_DIR"),
        SplitDirs(Environment.GetEnvironmentVariable("BASICSHEETS_LEGACY_DIRS")))
    { }

    // Dev/test override: BASICSHEETS_LEGACY_DIRS is a path-separator-delimited list.
    private static IReadOnlyList<string>? SplitDirs(string? value) =>
        string.IsNullOrWhiteSpace(value)
            ? null
            : value.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

    /// <param name="dataDir">Where the SQLite file lives. Defaults to
    /// LocalApplicationData/BasicSheets.</param>
    /// <param name="legacyDirs">Directories scanned for legacy JSON. Defaults to
    /// LocalApplicationData/data then the app base dir's wwwroot/data.</param>
    public BasicSheetsDb(string? dataDir, IReadOnlyList<string>? legacyDirs)
    {
        var localData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);

        DataDir = dataDir ?? Path.Combine(localData, "BasicSheets");
        Directory.CreateDirectory(DataDir);
        DbPath = Path.Combine(DataDir, "basicsheets.db");

        LegacyDirs = legacyDirs ?? new[]
        {
            Path.Combine(localData, "data"),
            Path.Combine(AppContext.BaseDirectory, "wwwroot", "data"),
        };

        Conn = new SQLiteConnection(DbPath);
        Conn.CreateTable<Sheet>();
        Conn.CreateTable<SheetFile>();
        Conn.CreateTable<Setlist>();
        Conn.CreateTable<SetlistItem>();
        Conn.CreateTable<Collection>();
        Conn.CreateTable<SheetCollectionLink>();
        Conn.CreateTable<Bookmark>();
        Conn.CreateTable<ViewerButton>();
        Conn.CreateTable<SettingRow>();
        Conn.CreateTable<SchemaMeta>();
        Conn.CreateTable<Thumbnail>();

        LegacyJsonMigrator.RunIfNeeded(this);
    }

    public T Locked<T>(Func<T> fn) { lock (_gate) { return fn(); } }
    public void Locked(Action act) { lock (_gate) { act(); } }

    public string? GetMeta(string key) => Locked(() => Conn.Find<SchemaMeta>(key)?.Value);
    public void SetMeta(string key, string value) =>
        Locked(() => Conn.InsertOrReplace(new SchemaMeta { Key = key, Value = value }));
}
