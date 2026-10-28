using BasicSheets.Shared.Data;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Services;

public class ViewerButtonService
{
    private readonly BasicSheetsDb _db;

    public ViewerButtonService(BasicSheetsDb db) => _db = db;

    public List<ViewerButton> GetAll() =>
        _db.Locked(() => _db.Conn.Table<ViewerButton>().ToList())
           .OrderBy(b => b.PdfFile == null || b.SourceNX < 0 ? 1 : 0)
           .ThenBy(b => b.SortOrder).ToList();

    public List<ViewerButton> GetForFile(string pdfFile)
    {
        var fp = pdfFile;
        return _db.Locked(() =>
                    _db.Conn.Table<ViewerButton>().Where(b => b.PdfFile == fp && b.SourceNX >= 0).ToList())
                  .OrderBy(b => b.SortOrder).ToList();
    }

    public List<ViewerButton> GetFloating() =>
        _db.Locked(() => _db.Conn.Table<ViewerButton>().ToList())
           .Where(b => b.PdfFile == null || b.SourceNX < 0)
           .OrderBy(b => b.SortOrder).ToList();

    public void Add(ViewerButton b) => _db.Locked(() => _db.Conn.Insert(b));

    public void Remove(string id) => _db.Locked(() => _db.Conn.Delete<ViewerButton>(id));

    public void Update(ViewerButton updated) => _db.Locked(() => _db.Conn.InsertOrReplace(updated));
}
