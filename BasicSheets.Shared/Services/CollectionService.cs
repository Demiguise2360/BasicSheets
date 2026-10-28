using BasicSheets.Shared.Data;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Services;

public class CollectionService
{
    private readonly BasicSheetsDb _db;

    public CollectionService(BasicSheetsDb db) => _db = db;

    public List<Collection> GetAll() =>
        _db.Locked(() => _db.Conn.Table<Collection>().ToList())
           .OrderBy(c => c.Name, StringComparer.CurrentCultureIgnoreCase).ToList();

    /// <summary>Find a collection by (case-insensitive) name, or create it; returns its id.</summary>
    public int GetOrCreate(string name)
    {
        var n = (name ?? "").Trim();
        if (string.IsNullOrWhiteSpace(n)) return 0;
        return _db.Locked(() =>
        {
            var existing = _db.Conn.Table<Collection>().ToList()
                .FirstOrDefault(c => string.Equals(c.Name, n, StringComparison.CurrentCultureIgnoreCase));
            if (existing != null) return existing.Id;
            var c = new Collection { Name = n };
            _db.Conn.Insert(c);
            return c.Id;
        });
    }

    public void Rename(int id, string name) => _db.Locked(() =>
    {
        var c = _db.Conn.Find<Collection>(id);
        if (c == null) return;
        c.Name = (name ?? "").Trim();
        _db.Conn.Update(c);
    });

    public void Delete(int id) => _db.Locked(() =>
    {
        _db.Conn.Delete<Collection>(id);
        _db.Conn.Execute("DELETE FROM sheet_collections WHERE CollectionId = ?", id);
    });

    public List<Collection> GetForSheet(int sheetId) => _db.Locked(() =>
    {
        var ids = _db.Conn.Table<SheetCollectionLink>().Where(l => l.SheetId == sheetId)
                     .ToList().Select(l => l.CollectionId).ToHashSet();
        return _db.Conn.Table<Collection>().ToList().Where(c => ids.Contains(c.Id)).ToList();
    });

    public List<Sheet> GetSheets(int collectionId) => _db.Locked(() =>
    {
        var ids = _db.Conn.Table<SheetCollectionLink>().Where(l => l.CollectionId == collectionId)
                     .ToList().Select(l => l.SheetId).ToHashSet();
        return _db.Conn.Table<Sheet>().ToList()
                  .Where(s => ids.Contains(s.Id)).OrderBy(s => s.Name).ToList();
    });

    public int CountSheets(int collectionId) =>
        _db.Locked(() => _db.Conn.Table<SheetCollectionLink>().Where(l => l.CollectionId == collectionId).Count());

    /// <summary>Replace the full set of collections a sheet belongs to, pruning any
    /// collections that end up with no sheets.</summary>
    public void SetSheetCollections(int sheetId, IEnumerable<int> collectionIds) => _db.Locked(() =>
    {
        _db.Conn.Execute("DELETE FROM sheet_collections WHERE SheetId = ?", sheetId);
        foreach (var cid in collectionIds.Distinct())
            _db.Conn.Insert(new SheetCollectionLink { SheetId = sheetId, CollectionId = cid });

        // prune now-empty collections
        var allColls = _db.Conn.Table<Collection>().ToList();
        foreach (var c in allColls)
        {
            var used = _db.Conn.Table<SheetCollectionLink>().Where(l => l.CollectionId == c.Id).Count() > 0;
            if (!used) _db.Conn.Delete<Collection>(c.Id);
        }
    });
}
