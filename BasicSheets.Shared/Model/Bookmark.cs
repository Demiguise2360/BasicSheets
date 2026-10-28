using SQLite;

namespace BasicSheets.Shared.Models;

[Table("bookmarks")]
public class Bookmark
{
    [PrimaryKey]
    public string Id { get; set; } = Guid.NewGuid().ToString();
    [Indexed]
    public string PdfFile { get; set; } = "";
    public string SheetName { get; set; } = "";
    public int Page { get; set; } = 1;
    public string Label { get; set; } = "";
    public string Color { get; set; } = "#387373";
    public DateTime CreatedAt { get; set; } = DateTime.Now;
}
