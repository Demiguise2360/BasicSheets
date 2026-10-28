using BasicSheets.Shared.Data;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Services;

public class BookmarkService
{
    private readonly BasicSheetsDb _db;

    public BookmarkService(BasicSheetsDb db) => _db = db;

    public List<Bookmark> GetAll() =>
        _db.Locked(() => _db.Conn.Table<Bookmark>().ToList());

    public List<Bookmark> GetForFile(string pdfFile)
    {
        if (string.IsNullOrEmpty(pdfFile)) return new();
        var fp = pdfFile;
        return _db.Locked(() =>
            _db.Conn.Table<Bookmark>().Where(b => b.PdfFile == fp).OrderBy(b => b.Page).ToList());
    }

    public bool HasOnPage(string pdfFile, int page)
    {
        if (string.IsNullOrEmpty(pdfFile)) return false;
        var fp = pdfFile;
        return _db.Locked(() =>
            _db.Conn.Table<Bookmark>().Where(b => b.PdfFile == fp && b.Page == page).Count() > 0);
    }

    public Bookmark? GetOnPage(string pdfFile, int page)
    {
        if (string.IsNullOrEmpty(pdfFile)) return null;
        var fp = pdfFile;
        return _db.Locked(() =>
            _db.Conn.Table<Bookmark>().Where(b => b.PdfFile == fp && b.Page == page).FirstOrDefault());
    }

    public void Add(Bookmark b)
    {
        if (b == null) return;
        _db.Locked(() => _db.Conn.Insert(b));
    }

    public void Remove(string id)
    {
        if (string.IsNullOrEmpty(id)) return;
        _db.Locked(() => _db.Conn.Delete<Bookmark>(id));
    }

    public void Update(Bookmark updated)
    {
        if (updated == null) return;
        _db.Locked(() => _db.Conn.InsertOrReplace(updated));
    }
}
