using System.Text.Json;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Data;

/// <summary>
/// One-time import of the old flat-JSON stores into SQLite. Imported files are renamed to
/// <c>*.json.bak</c> (never deleted). Guarded by a <c>schema_meta</c> flag so it runs once.
/// Old locations: sheets.json in LocalApplicationData/data; settings/bookmarks/viewer-buttons
/// in the app base dir's wwwroot/data (which this migration also moves off of).
/// </summary>
public static class LegacyJsonMigrator
{
    public static void RunIfNeeded(BasicSheetsDb db)
    {
        if (db.GetMeta("legacyMigrated") == "1") return;

        try
        {
            MigrateList<Sheet>(db, "sheets.json",
                items => { foreach (var s in items) { s.Id = 0; db.Conn.Insert(s); } });

            MigrateList<Bookmark>(db, "bookmarks.json",
                items => { foreach (var b in items) db.Conn.InsertOrReplace(b); });

            MigrateList<ViewerButton>(db, "viewer-buttons.json",
                items => { foreach (var v in items) db.Conn.InsertOrReplace(v); });

            MigrateList<Setting>(db, "settings.json",
                items => { foreach (var s in items)
                               db.Conn.InsertOrReplace(new SettingRow { Name = s.Name, Value = s.Value }); });
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[LegacyJsonMigrator] Migration fehlgeschlagen: {ex.Message}");
        }

        db.SetMeta("legacyMigrated", "1");
    }

    private static void MigrateList<T>(BasicSheetsDb db, string fileName, Action<List<T>> import)
    {
        var path = db.LegacyDirs
                     .Select(d => Path.Combine(d, fileName))
                     .FirstOrDefault(File.Exists);
        if (path == null) return;

        var items = ReadList<T>(path);
        if (items == null || items.Count == 0) return;

        db.Locked(() => db.Conn.RunInTransaction(() => import(items)));
        Backup(path);
    }

    private static List<T>? ReadList<T>(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            var json = File.ReadAllText(path);
            if (string.IsNullOrWhiteSpace(json)) return null;
            return JsonSerializer.Deserialize<List<T>>(json);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[LegacyJsonMigrator] {Path.GetFileName(path)} konnte nicht gelesen werden: {ex.Message}");
            return null;
        }
    }

    private static void Backup(string path)
    {
        try { if (File.Exists(path)) File.Move(path, path + ".bak", overwrite: true); }
        catch (Exception ex)
        {
            Console.WriteLine($"[LegacyJsonMigrator] Backup von {Path.GetFileName(path)} fehlgeschlagen: {ex.Message}");
        }
    }
}
