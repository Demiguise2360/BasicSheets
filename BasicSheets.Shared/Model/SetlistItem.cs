using SQLite;

namespace BasicSheets.Shared.Models;

/// <summary>A song's position within a <see cref="Setlist"/>.</summary>
[Table("setlist_items")]
public class SetlistItem
{
    [PrimaryKey, AutoIncrement]
    public int Id { get; set; }

    [Indexed]
    public int SetlistId { get; set; }

    public int SheetId { get; set; }
    public int Order   { get; set; } = 0;
}
