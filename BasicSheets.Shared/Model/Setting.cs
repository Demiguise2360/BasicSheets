namespace BasicSheets.Shared.Models;

public class Setting
{
    public string Name { get; set; } = "";
    public string Description { get; set; } = "";
    /// <summary>"toggle" | "select"</summary>
    public string Type { get; set; } = "";
    public string Value { get; set; } = "";
    /// <summary>For "select" type: each entry is "value|Label"</summary>
    public List<string>? Options { get; set; }
}
