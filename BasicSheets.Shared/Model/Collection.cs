using SQLite;

namespace BasicSheets.Shared.Models;

/// <summary>A tag-like grouping of songs (many-to-many via <see cref="SheetCollectionLink"/>).
/// Schema created in Phase 0; UI arrives in Phase 1.</summary>
[Table("collections")]
public class Collection
{
    [PrimaryKey, AutoIncrement]
    public int Id { get; set; }

    [Indexed]
    public string Name { get; set; } = "";
}
