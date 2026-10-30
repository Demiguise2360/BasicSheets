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

    public Sheet? GetById(int id) => _db.Locked(() => _db.Conn.Find<Sheet>(id));

    public void RemoveSheets(Sheet sheet)
    {
        if (sheet == null) return;

        try
        {
            if (!string.IsNullOrEmpty(sheet.FilePath))
            {
                var fileName = Path.GetFileName(sheet.FilePath);
                // Delete the physical file only if no other sheet or part still references it
                // (page-split songs share one PDF).
                bool shared = _db.Locked(() =>
                    _db.Conn.Table<Sheet>().Where(s => s.FilePath == sheet.FilePath && s.Id != sheet.Id).Count() > 0
                    || _db.Conn.Table<SheetFile>().ToList().Any(f => Path.GetFileName(f.Path) == fileName));
                if (!shared)
                {
                    var pdfPath = Path.Combine(UploadFolder, fileName);
                    if (File.Exists(pdfPath))
                        File.Delete(pdfPath);
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SheetService] PDF konnte nicht gelöscht werden: {ex.Message}");
        }

        _db.Locked(() =>
        {
            _db.Conn.Execute("DELETE FROM sheets WHERE Id = ?", sheet.Id);
            _db.Conn.Execute("DELETE FROM sheet_files WHERE SheetId = ?", sheet.Id);
        });
    }

    /// <summary>Set (or clear with "") only a song's backing-track audio file, without touching other fields.</summary>
    public void SetAudioPath(string filePath, string audioPath)
    {
        if (string.IsNullOrEmpty(filePath)) return;
        _db.Locked(() =>
        {
            var existing = _db.Conn.Table<Sheet>().FirstOrDefault(s => s.FilePath == filePath);
            if (existing == null) return;
            existing.AudioPath = audioPath ?? "";
            existing.Updated   = DateTime.Now;
            _db.Conn.Update(existing);
        });
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
            existing.PageStart  = updated.PageStart;
            existing.PageEnd    = updated.PageEnd;
            existing.Transpose  = updated.Transpose;
            existing.Rotation   = updated.Rotation;
            existing.PageOrder  = updated.PageOrder ?? "";
            existing.Bpm        = updated.Bpm;
            existing.Beats      = updated.Beats;
            existing.AudioPath  = updated.AudioPath ?? "";
            existing.PageMode   = updated.PageMode;
            existing.Updated    = DateTime.Now;

            _db.Conn.Update(existing);
        });
    }

    public int CountSheets() => _db.Locked(() => _db.Conn.Table<Sheet>().Count());
}
