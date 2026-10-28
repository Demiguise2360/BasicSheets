using SQLite;

namespace BasicSheets.Shared.Models;

/// <summary>An ordered, performable list of songs. Schema created in Phase 0; the editor
/// and continuous-paging UI arrive in Phase 1.</summary>
[Table("setlists")]
public class Setlist
{
    [PrimaryKey, AutoIncrement]
    public int Id { get; set; }

    public string   Name      { get; set; } = "";
    public int      SortOrder { get; set; } = 0;
    public DateTime Created   { get; set; } = DateTime.Now;
    public DateTime Updated   { get; set; } = DateTime.Now;
}
