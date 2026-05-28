namespace BasicSheets.Shared.Models;

public class Sheet
{
    public string Name { get; set; } = "";
    public string FilePath { get; set; } = "";
    public string Composer { get; set; } = "";
    public string Part { get; set; } = "";
    public DateTime Created { get; set; } = DateTime.Now;
    public DateTime Updated { get; set; } = DateTime.Now;

    public string GetInformation()
    {
        string res = "";

        res += this.Composer;

        if (res.Length > 0 && this.Part != string.Empty)
        {
            res += " - " + this.Part;
        } else if (this.Part != string.Empty)
        {
            res += this.Part;
        }

            return res;
    }
}
