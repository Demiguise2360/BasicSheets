using SQLite;

namespace BasicSheets.Shared.Models;

/// <summary>Internal key→value store for schema/migration bookkeeping.</summary>
[Table("schema_meta")]
public class SchemaMeta
{
    [PrimaryKey]
    public string Key { get; set; } = "";
    public string Value { get; set; } = "";
}
