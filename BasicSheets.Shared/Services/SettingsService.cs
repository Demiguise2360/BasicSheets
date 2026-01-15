using BasicSheets.Shared.Models;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace BasicSheets.Shared.Services
{
    public class SettingsService
    {
        private List<Setting> _settings = new();
        private readonly string filePath = Path.Combine(AppContext.BaseDirectory, "wwwroot", "data", "settings.json");

        public SettingsService()
        {
            LoadSettings();
        }

        private void LoadSettings()
        {
            if (!File.Exists(filePath))
            {
                Directory.CreateDirectory(Path.GetDirectoryName(filePath));
                List<Setting> settings = new ();
                settings.Add(new Setting { Name = "Zwei Seiten", Description = "Zwei Seiten anzeigen statt nur einer.", Type = "toggle", Value = "false" });
                settings.Add(new Setting { Name = "Start und End Hinweis", Description = "Hinweise, wenn man auf der ersten oder letzten Seite bei den Noten ist und versucht zu Blättern.", Type = "toggle", Value = "true" });
                string content = JsonSerializer.Serialize(settings, new JsonSerializerOptions
                {
                    WriteIndented = true
                });

                File.WriteAllText(filePath, content);
            }

            string json = File.ReadAllText(filePath);
            _settings = JsonSerializer.Deserialize<List<Setting>>(json) ?? new();
        }

        public void SaveSettings()
        {
            string json = JsonSerializer.Serialize(_settings, new JsonSerializerOptions
            {
                WriteIndented = true
            });

            File.WriteAllText(filePath, json);
        }

        public bool TwoPageLayout()
        {
            bool res = false;
            Setting? setting = _settings.FirstOrDefault(s => s.Name == "Zwei Seiten");
            if (setting != null)
            {
                bool.TryParse(setting.Value, out res);
            }
            return res;
        }

        public bool startAndEndHints()
        {
            bool res = true;
            Setting? setting = _settings.FirstOrDefault(s => s.Name == "Start und End Hinweis");
            if (setting != null)
            {
                bool.TryParse(setting.Value, out res);
            }
            return res;
        }

        public List<Setting> GetSettings()
        {
            return _settings;
        }
    }
}
