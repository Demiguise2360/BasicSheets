using BasicSheets.Shared.Data;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Services;

/// <summary>
/// Per-page crop rectangles for songs (stored in the <c>crops</c> table).
/// Rectangles are fractions 0–1 of the page; the viewer frames them to fill the screen.
/// </summary>
public class CropService
{
    private readonly BasicSheetsDb _db;

    public CropService(BasicSheetsDb db) => _db = db;

    /// <summary>All crops for a song, keyed by 1-based page.</summary>
    public Dictionary<int, Crop> GetCrops(int sheetId) =>
        _db.Locked(() => _db.Conn.Table<Crop>().Where(c => c.SheetId == sheetId).ToList())
           .GroupBy(c => c.Page)
           .ToDictionary(g => g.Key, g => g.First());

    public Crop? GetCrop(int sheetId, int page) =>
        _db.Locked(() => _db.Conn.Table<Crop>().FirstOrDefault(c => c.SheetId == sheetId && c.Page == page));

    public void SetCrop(int sheetId, int page, double l, double t, double r, double b)
    {
        _db.Locked(() =>
        {
            var existing = _db.Conn.Table<Crop>().FirstOrDefault(c => c.SheetId == sheetId && c.Page == page);
            if (existing == null)
            {
                _db.Conn.Insert(new Crop { SheetId = sheetId, Page = page, L = l, T = t, R = r, B = b });
            }
            else
            {
                existing.L = l; existing.T = t; existing.R = r; existing.B = b;
                _db.Conn.Update(existing);
            }
        });
    }

    /// <summary>Insert/update many page crops for a song in one transaction (batch auto-crop).</summary>
    public void SetMany(int sheetId, IEnumerable<(int Page, double L, double T, double R, double B)> crops)
    {
        var list = crops.ToList();
        if (list.Count == 0) return;
        _db.Locked(() => _db.Conn.RunInTransaction(() =>
        {
            foreach (var c in list)
            {
                var existing = _db.Conn.Table<Crop>().FirstOrDefault(x => x.SheetId == sheetId && x.Page == c.Page);
                if (existing == null)
                    _db.Conn.Insert(new Crop { SheetId = sheetId, Page = c.Page, L = c.L, T = c.T, R = c.R, B = c.B });
                else
                {
                    existing.L = c.L; existing.T = c.T; existing.R = c.R; existing.B = c.B;
                    _db.Conn.Update(existing);
                }
            }
        }));
    }

    public void RemoveCrop(int sheetId, int page) =>
        _db.Locked(() => _db.Conn.Execute("DELETE FROM crops WHERE SheetId = ? AND Page = ?", sheetId, page));

    public void RemoveAll(int sheetId) =>
        _db.Locked(() => _db.Conn.Execute("DELETE FROM crops WHERE SheetId = ?", sheetId));

    public int CountCrops(int sheetId) =>
        _db.Locked(() => _db.Conn.Table<Crop>().Count(c => c.SheetId == sheetId));
}
