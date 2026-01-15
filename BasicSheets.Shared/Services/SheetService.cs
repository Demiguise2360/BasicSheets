using BasicSheets.Shared.Models;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;


namespace BasicSheets.Shared.Services
{
    public class SheetService
    {
        private List<Sheet> _sheets = new();
        private readonly string filePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "data", "sheets.json");

        public SheetService()
        {
            string uploadFolder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "uploads");
            Directory.CreateDirectory(uploadFolder);

            LoadSheets();
        }

        public List<Sheet> GetSheets()
        {
            return _sheets;
        }

        public void AddSheets(Sheet sheet)
        {
            _sheets.Add(sheet);
            SaveSheets();
        }

        public void RemoveSheets(Sheet sheet)
        {
            try
            {
                File.Delete(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), sheet.FilePath));
            }
            catch (Exception e)
            {
                Console.WriteLine($"Error deleting file: {e.Message}");
                return;
            }
            _sheets.Remove(sheet);
            SaveSheets();
        }

        private void LoadSheets()
        {
            if (!File.Exists(filePath))
            {
                Directory.CreateDirectory(Path.GetDirectoryName(filePath));
                File.WriteAllText(filePath, "[]");
            }

            string json = File.ReadAllText(filePath);
            _sheets = JsonSerializer.Deserialize<List<Sheet>>(json) ?? new();
        }

        private void SaveSheets()
        {
            string json = JsonSerializer.Serialize(_sheets, new JsonSerializerOptions
            {
                WriteIndented = true
            });

            File.WriteAllText(filePath, json);
        }

        public int CountSheets()
        {
            return _sheets.Count;
        }

        public void UpdateSheet(Sheet updated)
        {
            var existing = _sheets.FirstOrDefault(s => s.FilePath == updated.FilePath);
            if (existing == null)
                return;

            existing.Name = updated.Name;
            existing.Composer = updated.Composer;
            existing.Part = updated.Part;

            SaveSheets();
        }
    }
}
