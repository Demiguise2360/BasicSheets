using BasicSheets.Shared.Data;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Services;

public class SheetService
{
    private readonly BasicSheetsDb _db;
    public string UploadFolder { get; }

    public SheetService(BasicSheetsDb db)
    {
        _db = db;
        UploadFolder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "uploads");

        try { Directory.CreateDirectory(UploadFolder); }
        catch (Exception ex)
        {
            Console.WriteLine($"[SheetService] Upload-Verzeichnis konnte nicht erstellt werden: {ex.Message}");
        }
    }

    public List<Sheet> GetSheets() =>
        _db.Locked(() => _db.Conn.Table<Sheet>().OrderBy(s => s.Name).ToList());

    public void AddSheets(Sheet sheet)
    {
        if (sheet == null) return;
        sheet.Created = DateTime.Now;
        sheet.Updated = DateTime.Now;
        _db.Locked(() => _db.Conn.Insert(sheet));
    }

    public void RemoveSheets(Sheet sheet)
    {
        if (sheet == null) return;

        try
        {
            if (!string.IsNullOrEmpty(sheet.FilePath))
            {
                var fileName = Path.GetFileName(sheet.FilePath);
                var pdfPath = Path.Combine(UploadFolder, fileName);
                if (File.Exists(pdfPath))
                    File.Delete(pdfPath);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SheetService] PDF konnte nicht gelöscht werden: {ex.Message}");
        }

        var fp = sheet.FilePath;
        _db.Locked(() => _db.Conn.Execute("DELETE FROM sheets WHERE FilePath = ?", fp));
    }

    public void UpdateSheet(Sheet updated)
    {
        if (updated == null) return;

        _db.Locked(() =>
        {
            var fp = updated.FilePath;
            var existing = _db.Conn.Table<Sheet>().FirstOrDefault(s => s.FilePath == fp);
            if (existing == null) return;

            existing.Name       = updated.Name ?? "";
            existing.Composer   = updated.Composer ?? "";
            existing.Part       = updated.Part ?? "";
            existing.Genre      = updated.Genre ?? "";
            existing.Year       = updated.Year ?? "";
            existing.Difficulty = updated.Difficulty ?? "";
            existing.Tags       = updated.Tags ?? "";
            existing.Remarks    = updated.Remarks ?? "";
            existing.Rating     = updated.Rating;
            existing.Keywords   = updated.Keywords ?? "";
            existing.PageMode   = updated.PageMode;
            existing.Updated    = DateTime.Now;

            _db.Conn.Update(existing);
        });
    }

    public int CountSheets() => _db.Locked(() => _db.Conn.Table<Sheet>().Count());
}
