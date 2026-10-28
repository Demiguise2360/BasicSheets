using SQLite;

namespace BasicSheets.Shared.Models;

[Table("sheets")]
public class Sheet
{
    [PrimaryKey, AutoIncrement]
    public int Id { get; set; }

    public string  Name       { get; set; } = "";
    [Indexed]
    public string  FilePath   { get; set; } = "";          // primary PDF (single-file model, Phase 0)
    public string  Composer   { get; set; } = "";
    public string  Part       { get; set; } = "";          // instrument / voice
    public string  Genre      { get; set; } = "";          // e.g. "Klassik", "Jazz"
    public string  Year       { get; set; } = "";          // free-text, e.g. "1820"
    public string  Difficulty { get; set; } = "";          // "" | "beginner" | "intermediate" | "advanced" | "expert"
    public string  Tags       { get; set; } = "";          // comma-separated
    public string  Remarks    { get; set; } = "";          // free-text notes / remarks
    public int     Rating     { get; set; } = 0;           // 0–5, reserved for library phase
    public string  Keywords   { get; set; } = "";          // reserved for library phase
    public DateTime Created   { get; set; } = DateTime.Now;
    public DateTime Updated   { get; set; } = DateTime.Now;

    /// <summary>null = use global setting; "single" | "two" | "half-turn" | "scroll-v"</summary>
    public string? PageMode   { get; set; } = null;

    /// <summary>Files of this song. Stored in the <c>sheet_files</c> table, not on the
    /// sheet row — reserved for multi-file songs (Phase 2). Not persisted here.</summary>
    [Ignore]
    public List<SheetFile> Files { get; set; } = new();

    /// <summary>Short subtitle shown beneath the name in the list.</summary>
    public string GetInformation()
    {
        var parts = new List<string>();
        if (!string.IsNullOrWhiteSpace(Composer)) parts.Add(Composer);
        if (!string.IsNullOrWhiteSpace(Part))     parts.Add(Part);
        if (!string.IsNullOrWhiteSpace(Genre))    parts.Add(Genre);
        if (!string.IsNullOrWhiteSpace(Year))     parts.Add(Year);
        return string.Join(" · ", parts);
    }
}
