using SQLite;

namespace BasicSheets.Shared.Models;

/// <summary>Cached first-page preview for a sheet's PDF, stored as a small JPEG data URL.
/// Keyed by the file name (<c>Path.GetFileName(sheet.FilePath)</c>). Generated once when a
/// sheet is first viewed, reusing the viewer's already-loaded pdf.js document.</summary>
[Table("thumbnails")]
public class Thumbnail
{
    [PrimaryKey]
    public string FilePath { get; set; } = "";
    public string DataUrl { get; set; } = "";
    public DateTime UpdatedAt { get; set; } = DateTime.Now;
}
