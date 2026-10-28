using SQLite;

namespace BasicSheets.Shared.Models;

/// <summary>Join row linking a <see cref="Sheet"/> to a <see cref="Collection"/>.</summary>
[Table("sheet_collections")]
public class SheetCollectionLink
{
    [PrimaryKey, AutoIncrement]
    public int Id { get; set; }

    [Indexed]
    public int SheetId { get; set; }

    [Indexed]
    public int CollectionId { get; set; }
}
