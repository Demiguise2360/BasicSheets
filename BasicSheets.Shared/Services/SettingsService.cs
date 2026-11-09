using BasicSheets.Shared.Data;
using BasicSheets.Shared.Models;

namespace BasicSheets.Shared.Services;

public class SettingsService
{
    private readonly BasicSheetsDb _db;
    private List<Setting> _settings = new();

    public SettingsService(BasicSheetsDb db)
    {
        _db = db;
        LoadSettings();
    }

    private void LoadSettings()
    {
        // Build the canonical default list in code, then overlay the user's saved choices.
        _settings = new List<Setting>();
        MigrateAndEnsureDefaults();

        try
        {
            var rows = _db.Locked(() => _db.Conn.Table<SettingRow>().ToList());
            foreach (var r in rows)
            {
                var s = _settings.FirstOrDefault(x => x.Name == r.Name);
                if (s != null && IsValidValue(s, r.Value))
                    s.Value = r.Value;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SettingsService] Fehler beim Laden der Einstellungen: {ex.Message}");
        }

        SaveSettings();
    }

    private static bool IsValidValue(Setting s, string value)
    {
        if (s.Type == "toggle") return value is "true" or "false";
        if (s.Type == "select") return s.Options == null || s.Options.Any(o => o.Split('|', 2)[0] == value);
        return true;
    }

    private void MigrateAndEnsureDefaults()
    {
        var oldTwo = _settings.FirstOrDefault(s => s.Name == "Zwei Seiten");
        if (oldTwo != null)
        {
            var migratedVal = oldTwo.Value == "true" ? "two" : "single";
            _settings.Remove(oldTwo);
            if (!_settings.Any(s => s.Name == "Seitenansicht"))
                _settings.Insert(0, MakePageModeSetting(migratedVal));
        }

        var existing = _settings.FirstOrDefault(s => s.Name == "Seitenansicht");
        if (existing != null)
        {
            if (existing.Value is "half" or "auto" or "scroll-h")
                existing.Value = "single";
            existing.Options = MakePageModeOptions();
        }

        AddIfMissing(MakePageModeSetting("single"));
        AddIfMissing(new Setting
        {
            Name        = "Dunkles Design",
            Description = "Augen schonen bei schlechtem Licht.",
            Type        = "toggle",
            Value       = "false"
        });
        AddIfMissing(new Setting
        {
            Name        = "Start und End Hinweis",
            Description = "Hinweis anzeigen, wenn man am Anfang oder Ende der Noten blättert.",
            Type        = "toggle",
            Value       = "true"
        });
        AddIfMissing(new Setting
        {
            Name        = "Toolbar automatisch ausblenden",
            Description = "Die obere Leiste wird nach einigen Sekunden ausgeblendet.",
            Type        = "toggle",
            Value       = "false"
        });
        AddIfMissing(new Setting
        {
            Name        = "Seitenübergang animieren",
            Description = "Weiche Überblendung beim Blättern (kann auf älteren Geräten langsamer sein).",
            Type        = "toggle",
            Value       = "false"
        });
        AddIfMissing(new Setting
        {
            Name        = "Bildschirm wach halten",
            Description = "Verhindert, dass der Bildschirm sich ausschaltet während Noten gezeigt werden.",
            Type        = "toggle",
            Value       = "false"
        });
        AddIfMissing(new Setting
        {
            Name        = "Seiten automatisch umblättern",
            Description = "Seiten nach einer bestimmten Zeit automatisch weiterblättern.",
            Type        = "select",
            Value       = "off",
            Options     = ["off|Aus", "30|30 Sekunden", "60|1 Minute", "120|2 Minuten"]
        });
        AddIfMissing(new Setting
        {
            Name        = "Viewer-Hintergrund",
            Description = "Hintergrundfarbe hinter den Noten.",
            Type        = "select",
            Value       = "dark",
            Options     = ["dark|Dunkel", "gray|Grau", "white|Hell"]
        });
        AddIfMissing(new Setting
        {
            Name        = "Noten invertieren",
            Description = "Nachtmodus: kehrt die Farben der Noten um (weiß auf schwarz) für dunkle Umgebungen.",
            Type        = "toggle",
            Value       = "false"
        });
        var oldAutoZoom = _settings.FirstOrDefault(s => s.Name == "Auto-Zoom");
        if (oldAutoZoom != null)
            _settings.Remove(oldAutoZoom);

        var oldScaling = _settings.FirstOrDefault(s => s.Name == "Seitenskalierung");
        if (oldScaling != null)
            _settings.Remove(oldScaling);
        AddIfMissing(new Setting
        {
            Name        = "Seitenzahlen",
            Description = "Seitennummern anzeigen.",
            Type        = "toggle",
            Value       = "true"
        });
        AddIfMissing(new Setting
        {
            Name        = "Stiftdicke",
            Description = "Standardbreite für Annotationen.",
            Type        = "select",
            Value       = "medium",
            Options     = ["thin|Dünn", "medium|Mittel", "thick|Dick"]
        });
        AddIfMissing(new Setting
        {
            Name        = "Stiftfarbe",
            Description = "Standardfarbe für Annotationen.",
            Type        = "select",
            Value       = "black",
            Options     = ["black|Schwarz", "red|Rot", "blue|Blau", "green|Grün"]
        });
        AddIfMissing(new Setting
        {
            Name        = "Standard-Notizwerkzeug",
            Description = "Werkzeug, das beim Öffnen des Editors automatisch ausgewählt wird.",
            Type        = "select",
            Value       = "pen",
            Options     = ["pen|Stift", "marker|Marker", "text|Text"]
        });
        AddIfMissing(new Setting
        {
            Name        = "Letzte Seite merken",
            Description = "Beim Öffnen direkt zur zuletzt gelesenen Seite springen.",
            Type        = "toggle",
            Value       = "true"
        });
        AddIfMissing(new Setting
        {
            Name        = "Sortierung",
            Description = "Reihenfolge der Noten in der Liste.",
            Type        = "select",
            Value       = "name",
            Options     = ["name|Name", "composer|Komponist", "date|Datum", "difficulty|Schwierigkeit"]
        });

        var gestureActions = new List<string>
        {
            "none|Nichts",
            "zoom_reset|Zoom zurücksetzen",
            "go_home|Startseite",
            "add_bookmark|Lesezeichen setzen",
            "next_page|Nächste Seite",
            "prev_page|Vorherige Seite",
            "first_page|Erste Seite",
            "last_page|Letzte Seite",
            "toggle_edit|Bearbeitungsmodus",
            "open_search|Suche öffnen",
            "open_grid|Seitenübersicht",
            "toggle_metronome|Metronom",
            "toggle_audio|Audio abspielen",
            "toggle_lock|Tippen sperren"
        };
        AddIfMissing(new Setting
        {
            Name = "Geste: Von unten wischen",
            Description = "Aktion beim Wischen vom unteren Bildschirmrand nach oben.",
            Type = "select", Value = "go_home", Options = gestureActions
        });
        AddIfMissing(new Setting
        {
            Name = "Geste: Lang halten",
            Description = "Aktion beim langen Gedrückthalten auf die Noten.",
            Type = "select", Value = "add_bookmark", Options = gestureActions
        });
        AddIfMissing(new Setting
        {
            Name = "Geste: 2 Finger Doppeltippen",
            Description = "Aktion beim Doppeltippen mit 2 Fingern.",
            Type = "select", Value = "zoom_reset", Options = gestureActions
        });
        AddIfMissing(new Setting
        {
            Name = "Geste: 3 Finger links",
            Description = "Aktion beim Wischen mit 3 Fingern nach links.",
            Type = "select", Value = "next_page", Options = gestureActions
        });
        AddIfMissing(new Setting
        {
            Name = "Geste: 3 Finger rechts",
            Description = "Aktion beim Wischen mit 3 Fingern nach rechts.",
            Type = "select", Value = "prev_page", Options = gestureActions
        });
        AddIfMissing(new Setting
        {
            Name        = "Stift: Bearbeitungsmodus",
            Description = "Stift automatisch erkennen: Nähert sich ein Stift → Bearbeiten; Finger danach → Beenden.",
            Type        = "toggle",
            Value       = "true"
        });
        AddIfMissing(new Setting
        {
            Name        = "Stift: Seitentaste",
            Description = "Aktion der vorderen Stifttaste im Bearbeitungsmodus.",
            Type        = "select",
            Value       = "hold",
            Options     = ["hold|Halten zum Radieren", "toggle|Umschalten zum Radierer", "off|Aus"]
        });
        AddIfMissing(new Setting
        {
            Name        = "Stift: Druckstärke",
            Description = "Strichbreite folgt dem Druck des Stifts (nur bei druckempfindlichem Stift).",
            Type        = "toggle",
            Value       = "true"
        });
        AddIfMissing(new Setting
        {
            Name        = "Stift: Handballenerkennung",
            Description = "Finger- und Handballenberührungen ignorieren, solange der Stift zeichnet.",
            Type        = "toggle",
            Value       = "true"
        });
    }

    private static List<string> MakePageModeOptions() =>
    [
        "single|Einzelne Seite",
        "two|Zwei Seiten",
        "half-turn|Halbe Seite",
        "scroll-v|Vertikal scrollen"
    ];

    private static Setting MakePageModeSetting(string defaultVal = "single") => new()
    {
        Name        = "Seitenansicht",
        Description = "Globale Standardansicht für alle Noten. Kann pro Stück überschrieben werden.",
        Type        = "select",
        Value       = defaultVal,
        Options     = MakePageModeOptions()
    };

    private void AddIfMissing(Setting s)
    {
        if (!_settings.Any(x => x.Name == s.Name))
            _settings.Add(s);
    }

    public void SaveSettings()
    {
        try
        {
            _db.Locked(() => _db.Conn.RunInTransaction(() =>
            {
                foreach (var s in _settings)
                    _db.Conn.InsertOrReplace(new SettingRow { Name = s.Name, Value = s.Value });
            }));
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SettingsService] Fehler beim Speichern der Einstellungen: {ex.Message}");
        }
    }

    public List<Setting> GetSettings() => _settings;

    public string GetPageMode()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Seitenansicht");
        return s?.Value ?? "single";
    }

    public bool StartAndEndHints()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Start und End Hinweis");
        return s == null || s.Value != "false";
    }

    public bool startAndEndHints() => StartAndEndHints();

    public bool AutoHideToolbar()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Toolbar automatisch ausblenden");
        return s?.Value == "true";
    }

    public bool TwoPageLayout() => GetPageMode() == "two";

    public bool KeepScreenOn()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Bildschirm wach halten");
        return s?.Value == "true";
    }

    public int AutoPageTurnSeconds()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Seiten automatisch umblättern");
        if (s == null || s.Value == "off") return 0;
        return int.TryParse(s.Value, out var v) ? v : 0;
    }

    public string GetViewerBackground()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Viewer-Hintergrund");
        return s?.Value ?? "dark";
    }

    public bool InvertColors()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Noten invertieren");
        return s?.Value == "true";
    }

    public bool WarmReading()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Warmer Lesemodus");
        return s?.Value == "true";
    }

    public string GetDefaultTool()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Standard-Notizwerkzeug");
        return s?.Value ?? "pen";
    }

    public bool RememberLastPage()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Letzte Seite merken");
        return s == null || s.Value != "false";
    }

    public string GetSortOrder()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Sortierung");
        return s?.Value ?? "name";
    }

    public void SetSortOrder(string value)
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Sortierung");
        if (s == null) _settings.Add(new Setting { Name = "Sortierung", Value = value });
        else s.Value = value;
        SaveSettings();
    }

    public string GetGestureAction(string gestureName)
    {
        var s = _settings.FirstOrDefault(x => x.Name == gestureName);
        return s?.Value ?? "none";
    }

    public bool StylusAutoEdit()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Stift: Bearbeitungsmodus");
        return s == null || s.Value != "false";
    }

    public string StylusButtonMode()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Stift: Seitentaste");
        return s?.Value ?? "hold";
    }

    public bool PenPressure()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Stift: Druckstärke");
        return s == null || s.Value != "false";
    }

    public bool PalmReject()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Stift: Handballenerkennung");
        return s == null || s.Value != "false";
    }

    public bool IsDarkMode()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Dunkles Design");
        return s?.Value == "true";
    }

    public string GetPageScaling()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Seitenskalierung");
        return s?.Value ?? "fit-screen";
    }

    public bool ShowPageNumbers()
    {
        var s = _settings.FirstOrDefault(x => x.Name == "Seitenzahlen");
        return s == null || s.Value != "false";
    }
}
