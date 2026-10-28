using SQLite;

namespace BasicSheets.Shared.Models;

/// <summary>Persisted name→value pair for a user setting. The full <see cref="Setting"/>
/// objects (with description/type/options) are rebuilt in code; only the chosen value is
/// stored here.</summary>
[Table("settings_kv")]
public class SettingRow
{
    [PrimaryKey]
    public string Name { get; set; } = "";
    public string Value { get; set; } = "";
}
