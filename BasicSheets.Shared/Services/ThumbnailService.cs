using BasicSheets.Shared.Data;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Services;

public class ThumbnailService
{
    private readonly BasicSheetsDb _db;

    public ThumbnailService(BasicSheetsDb db) => _db = db;

    public bool Has(string file)
    {
        if (string.IsNullOrEmpty(file)) return false;
        var f = file;
        return _db.Locked(() => _db.Conn.Table<Thumbnail>().Where(t => t.FilePath == f).Count() > 0);
    }

    public string? Get(string file)
    {
        if (string.IsNullOrEmpty(file)) return null;
        var f = file;
        return _db.Locked(() => _db.Conn.Table<Thumbnail>().Where(t => t.FilePath == f).FirstOrDefault()?.DataUrl);
    }

    public void Set(string file, string dataUrl)
    {
        if (string.IsNullOrEmpty(file) || string.IsNullOrEmpty(dataUrl)) return;
        _db.Locked(() => _db.Conn.InsertOrReplace(new Thumbnail { FilePath = file, DataUrl = dataUrl, UpdatedAt = DateTime.Now }));
    }

    public void Remove(string file)
    {
        if (string.IsNullOrEmpty(file)) return;
        var f = file;
        _db.Locked(() => _db.Conn.Execute("DELETE FROM thumbnails WHERE FilePath = ?", f));
    }

    /// <summary>All cached thumbnails as file name → data URL, for the library list.</summary>
    public Dictionary<string, string> GetAll() =>
        _db.Locked(() => _db.Conn.Table<Thumbnail>().ToList())
           .ToDictionary(t => t.FilePath, t => t.DataUrl);
}
