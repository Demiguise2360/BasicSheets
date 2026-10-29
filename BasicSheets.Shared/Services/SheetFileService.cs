using BasicSheets.Shared.Data;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Services;

/// <summary>
/// Additional files ("parts") belonging to a song. The song's primary file stays on
/// <see cref="Sheet.FilePath"/>; extra parts live in the <c>sheet_files</c> table. A song's
/// full ordered file list is the primary followed by its parts.
/// </summary>
public class SheetFileService
{
    private readonly BasicSheetsDb _db;
    public string UploadFolder { get; }

    public SheetFileService(BasicSheetsDb db)
    {
        _db = db;
        UploadFolder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "uploads");
        try { Directory.CreateDirectory(UploadFolder); } catch { }
    }

    public List<SheetFile> GetParts(int sheetId) =>
        _db.Locked(() => _db.Conn.Table<SheetFile>().Where(f => f.SheetId == sheetId).OrderBy(f => f.Order).ToList());

    public int CountParts(int sheetId) =>
        _db.Locked(() => _db.Conn.Table<SheetFile>().Where(f => f.SheetId == sheetId).Count());

    public void AddPart(int sheetId, string path, string label) => _db.Locked(() =>
    {
        var existing = _db.Conn.Table<SheetFile>().Where(f => f.SheetId == sheetId).ToList();
        var maxOrder = existing.Count == 0 ? -1 : existing.Max(f => f.Order);
        _db.Conn.Insert(new SheetFile
        {
            SheetId = sheetId, Path = path, Kind = "pdf",
            Label = label ?? "", Order = maxOrder + 1
        });
    });

    public void RemovePart(int fileId)
    {
        var sf = _db.Locked(() => _db.Conn.Find<SheetFile>(fileId));
        if (sf == null) return;
        try
        {
            var p = Path.Combine(UploadFolder, Path.GetFileName(sf.Path));
            if (File.Exists(p)) File.Delete(p);
        }
        catch (Exception ex) { Console.WriteLine($"[SheetFileService] Part-Datei konnte nicht gelöscht werden: {ex.Message}"); }

        _db.Locked(() => _db.Conn.Delete<SheetFile>(fileId));
    }

    public void UpdateLabel(int fileId, string label) => _db.Locked(() =>
    {
        var sf = _db.Conn.Find<SheetFile>(fileId);
        if (sf == null) return;
        sf.Label = label ?? "";
        _db.Conn.Update(sf);
    });

    public void Move(int sheetId, int fileId, int dir) => _db.Locked(() =>
    {
        var items = _db.Conn.Table<SheetFile>().Where(f => f.SheetId == sheetId).OrderBy(f => f.Order).ToList();
        var idx = items.FindIndex(f => f.Id == fileId);
        if (idx < 0) return;
        var swap = idx + dir;
        if (swap < 0 || swap >= items.Count) return;

        (items[idx].Order, items[swap].Order) = (items[swap].Order, items[idx].Order);
        _db.Conn.Update(items[idx]);
        _db.Conn.Update(items[swap]);
    });

    /// <summary>The sheet that owns a file name — its primary file or one of its parts.</summary>
    public Sheet? FindSheetByFile(string file) => _db.Locked(() =>
    {
        var sheets = _db.Conn.Table<Sheet>().ToList();
        var byPrimary = sheets.FirstOrDefault(s => Path.GetFileName(s.FilePath) == file);
        if (byPrimary != null) return byPrimary;

        var part = _db.Conn.Table<SheetFile>().ToList().FirstOrDefault(f => Path.GetFileName(f.Path) == file);
        return part == null ? null : sheets.FirstOrDefault(s => s.Id == part.SheetId);
    });

    /// <summary>Ordered (path, label) list for a song: primary first, then parts.</summary>
    public List<(string Path, string Label)> GetSongFiles(Sheet sheet)
    {
        var list = new List<(string, string)>
        {
            (sheet.FilePath, string.IsNullOrWhiteSpace(sheet.Part) ? "Hauptstimme" : sheet.Part)
        };
        foreach (var p in GetParts(sheet.Id))
            list.Add((p.Path, string.IsNullOrWhiteSpace(p.Label)
                ? System.IO.Path.GetFileNameWithoutExtension(p.Path) : p.Label));
        return list;
    }
}
