using SQLite;

namespace BasicSheets.Shared.Models;

/// <summary>
/// A crop rectangle for one page of a song, stored as fractions (0–1) of the page
/// so it is resolution-independent. The viewer frames [L,T]–[R,B] to fill the screen.
/// </summary>
[Table("crops")]
public class Crop
{
    [PrimaryKey, AutoIncrement]
    public int Id { get; set; }

    [Indexed]
    public int SheetId { get; set; }

    public int Page { get; set; }    // 1-based page (always 1 for image songs)

    public double L { get; set; }
    public double T { get; set; }
    public double R { get; set; } = 1;
    public double B { get; set; } = 1;
}
