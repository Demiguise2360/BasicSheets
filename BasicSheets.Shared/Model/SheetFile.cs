using SQLite;

namespace BasicSheets.Shared.Models;

/// <summary>
/// One file belonging to a <see cref="Sheet"/> (song). Reserved for multi-file songs
/// (different parts, mixed PDF/image/text). Created in Phase 0; populated from Phase 2.
/// </summary>
[Table("sheet_files")]
public class SheetFile
{
    [PrimaryKey, AutoIncrement]
    public int Id { get; set; }

    [Indexed]
    public int SheetId { get; set; }

    public string Path { get; set; } = "";
    public string Kind { get; set; } = "pdf";   // pdf | image | text
    public int    PageStart { get; set; } = 0;   // 0 = whole file
    public int    PageEnd   { get; set; } = 0;
    public int    Order     { get; set; } = 0;
    public string Label     { get; set; } = "";  // instrument/voice, shown in the file switcher
}
