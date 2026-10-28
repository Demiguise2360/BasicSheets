using SQLite;

namespace BasicSheets.Shared.Models;

[Table("viewer_buttons")]
public class ViewerButton
{
    [PrimaryKey]
    public string Id      { get; set; } = Guid.NewGuid().ToString();
    public string Label   { get; set; } = "";
    public string Action  { get; set; } = "jump";   // jump | first | last | bookmark | go_home | toggle_edit
    public string Param   { get; set; } = "";        // legacy / fallback target page (string)
    public int    SortOrder { get; set; } = 0;

    // ── Placed-button fields (null/negative = legacy floating button) ──
    [Indexed]
    public string? PdfFile    { get; set; }          // which PDF this button belongs to
    public int     SourcePage { get; set; } = 0;     // PDF page number (1-based) of the button
    public double  SourceNX   { get; set; } = -1;    // normalized X in [0,1], -1 = not placed
    public double  SourceNY   { get; set; } = -1;    // normalized Y in [0,1]
    public string  Color      { get; set; } = "#387373";

    // ── Jump target (optional visual destination marker) ──
    public int    TargetPage  { get; set; } = 1;
    public double TargetNX    { get; set; } = -1;    // -1 = no visual marker
    public double TargetNY    { get; set; } = -1;
}
