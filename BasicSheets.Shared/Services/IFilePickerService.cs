namespace BasicSheets.Shared.Services;

public interface IFilePickerService
{
    Task<PickedFile?> PickPdfAsync();
}

public class PickedFile
{
    public string FileName { get; set; } = "";
    public string FullPath { get; set; } = "";
    public long Size { get; set; }
}
