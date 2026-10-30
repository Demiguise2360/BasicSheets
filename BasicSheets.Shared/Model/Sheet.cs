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
    public int     PageStart  { get; set; } = 0;           // 0 = whole file; >0 = this song is pages [PageStart,PageEnd] of FilePath
    public int     PageEnd    { get; set; } = 0;
    public int     Transpose  { get; set; } = 0;           // semitone offset for ChordPro/text songs (-11..11)
    public int     Rotation   { get; set; } = 0;           // display rotation in degrees (0 | 90 | 180 | 270)
    public string  PageOrder  { get; set; } = "";          // custom page sequence, CSV of 1-based pages ("" = natural)
    public int     Bpm        { get; set; } = 0;           // per-song metronome tempo (0 = default 100)
    public int     Beats      { get; set; } = 0;           // per-song metronome beats/measure (0 = default 4)
    public string  AudioPath  { get; set; } = "";          // optional backing-track file name in UploadFolder ("" = none)
    public DateTime Created   { get; set; } = DateTime.Now;
    public DateTime Updated   { get; set; } = DateTime.Now;

    /// <summary>null = use global setting; "single" | "two" | "half-turn" | "scroll-v"</summary>
    public string? PageMode   { get; set; } = null;

    /// <summary>True when this song is a page range of a shared PDF (page-split).</summary>
    [Ignore]
    public bool HasRange => PageStart > 0 && PageEnd >= PageStart;

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
