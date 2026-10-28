using BasicSheets.Shared.Data;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Services;

public class SetlistService
{
    private readonly BasicSheetsDb _db;

    public SetlistService(BasicSheetsDb db) => _db = db;

    public List<Setlist> GetAll() =>
        _db.Locked(() => _db.Conn.Table<Setlist>().ToList())
           .OrderBy(s => s.SortOrder).ThenBy(s => s.Name).ToList();

    public Setlist? Get(int id) => _db.Locked(() => _db.Conn.Find<Setlist>(id));

    public int Create(string name)
    {
        var sl = new Setlist { Name = (name ?? "").Trim(), Created = DateTime.Now, Updated = DateTime.Now };
        if (string.IsNullOrWhiteSpace(sl.Name)) sl.Name = "Neue Setlist";
        _db.Locked(() => _db.Conn.Insert(sl));
        return sl.Id;
    }

    public void Rename(int id, string name) => _db.Locked(() =>
    {
        var sl = _db.Conn.Find<Setlist>(id);
        if (sl == null) return;
        sl.Name = (name ?? "").Trim();
        sl.Updated = DateTime.Now;
        _db.Conn.Update(sl);
    });

    public void Delete(int id) => _db.Locked(() =>
    {
        _db.Conn.Delete<Setlist>(id);
        _db.Conn.Execute("DELETE FROM setlist_items WHERE SetlistId = ?", id);
    });

    public int CountItems(int setlistId) =>
        _db.Locked(() => _db.Conn.Table<SetlistItem>().Where(i => i.SetlistId == setlistId).Count());

    /// <summary>Items resolved to their <see cref="Sheet"/>s, in order.</summary>
    public List<Sheet> GetSheets(int setlistId) => _db.Locked(() =>
    {
        var items = _db.Conn.Table<SetlistItem>().Where(i => i.SetlistId == setlistId).OrderBy(i => i.Order).ToList();
        var result = new List<Sheet>();
        foreach (var it in items)
        {
            var s = _db.Conn.Find<Sheet>(it.SheetId);
            if (s != null) result.Add(s);
        }
        return result;
    });

    public List<SetlistItem> GetItems(int setlistId) => _db.Locked(() =>
        _db.Conn.Table<SetlistItem>().Where(i => i.SetlistId == setlistId).OrderBy(i => i.Order).ToList());

    public void AddSheet(int setlistId, int sheetId) => _db.Locked(() =>
    {
        var existing = _db.Conn.Table<SetlistItem>().Where(i => i.SetlistId == setlistId).ToList();
        var maxOrder = existing.Count == 0 ? -1 : existing.Max(i => i.Order);
        _db.Conn.Insert(new SetlistItem { SetlistId = setlistId, SheetId = sheetId, Order = maxOrder + 1 });
    });

    public void RemoveItem(int itemId) => _db.Locked(() => _db.Conn.Delete<SetlistItem>(itemId));

    /// <summary>Move an item up (direction = -1) or down (+1) within its setlist.</summary>
    public void Move(int setlistId, int itemId, int direction) => _db.Locked(() =>
    {
        var items = _db.Conn.Table<SetlistItem>().Where(i => i.SetlistId == setlistId).OrderBy(i => i.Order).ToList();
        var idx = items.FindIndex(i => i.Id == itemId);
        if (idx < 0) return;
        var swap = idx + direction;
        if (swap < 0 || swap >= items.Count) return;

        (items[idx].Order, items[swap].Order) = (items[swap].Order, items[idx].Order);
        _db.Conn.Update(items[idx]);
        _db.Conn.Update(items[swap]);
    });
}
