namespace BasicSheets.Shared.Services;

/// <summary>
/// Helpers for a song's custom page order — a sequence of 1-based PDF page numbers
/// (reordered, with pages duplicated or omitted), stored as CSV on the Sheet.
/// </summary>
public static class PageOrderUtil
{
    /// <summary>Parse the CSV into a list of valid 1-based pages (drops out-of-range entries).</summary>
    public static List<int> Parse(string? csv, int totalPages)
    {
        var list = new List<int>();
        if (string.IsNullOrWhiteSpace(csv) || totalPages <= 0) return list;
        foreach (var part in csv.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
            if (int.TryParse(part, out var n) && n >= 1 && n <= totalPages)
                list.Add(n);
        return list;
    }

    public static string Serialize(IEnumerable<int> order) => string.Join(",", order);

    /// <summary>True when the order is exactly 1,2,…,totalPages (i.e. no custom order).</summary>
    public static bool IsNatural(IReadOnlyList<int> order, int totalPages)
    {
        if (order.Count != totalPages) return false;
        for (int i = 0; i < order.Count; i++)
            if (order[i] != i + 1) return false;
        return true;
    }
}
