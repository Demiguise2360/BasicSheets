using System.Text;

namespace BasicSheets.Shared.Services;

// ── Parsed representation of a ChordPro / text song ─────────────────────────
public enum ChordLineKind { Lyric, Comment, Section, Tab, Empty }

/// <summary>A lyric run with the chord that sits above its first character ("" = none).</summary>
public class ChordSegment
{
    public string Chord { get; set; } = "";
    public string Lyric { get; set; } = "";
}

public class ChordLine
{
    public ChordLineKind Kind { get; set; }
    public List<ChordSegment> Segments { get; set; } = new();   // Lyric lines
    public string Text { get; set; } = "";                      // Comment / Section / Tab
    public string Section { get; set; } = "";                   // chorus | verse | bridge | tab | ""
}

public class ChordSong
{
    public string Title { get; set; } = "";
    public string Subtitle { get; set; } = "";
    public string Artist { get; set; } = "";
    public string Composer { get; set; } = "";
    public string Key { get; set; } = "";
    public string Capo { get; set; } = "";
    public string Tempo { get; set; } = "";
    public List<ChordLine> Lines { get; set; } = new();
    public bool HasChords { get; set; }
    /// <summary>No directives and no [chords] → render verbatim (preserves manual alignment).</summary>
    public bool IsPlainText { get; set; }
    public string RawText { get; set; } = "";
}

/// <summary>
/// Minimal, dependency-free ChordPro parser. Handles the common directives
/// ({title}, {subtitle}, {comment}, {start_of_chorus}, {key}, {capo}, …), inline
/// [chord] markup, and tab blocks. Plain .txt without ChordPro markup is flagged
/// <see cref="ChordSong.IsPlainText"/> so the viewer can render it verbatim.
/// </summary>
public static class ChordPro
{
    public static readonly string[] TextExtensions =
        { ".cho", ".chopro", ".chordpro", ".crd", ".pro", ".txt" };

    public static bool IsTextFile(string? file) =>
        file != null && TextExtensions.Any(e => file.EndsWith(e, StringComparison.OrdinalIgnoreCase));

    public static ChordSong Parse(string content, string? ext = null)
    {
        content ??= "";
        var song = new ChordSong { RawText = content.Replace("\r\n", "\n").Replace("\r", "\n") };

        bool sawDirective = false;
        string section = "";
        bool inTab = false;

        foreach (var raw in song.RawText.Split('\n'))
        {
            var trimmed = raw.Trim();

            // Directive: {name} / {name: value}. Detected even inside a tab block so {eot} ends it.
            if (trimmed.Length >= 2 && trimmed[0] == '{' && trimmed[^1] == '}')
            {
                sawDirective = true;
                var body = trimmed[1..^1].Trim();
                string name, value;
                int colon = body.IndexOf(':');
                if (colon >= 0) { name = body[..colon].Trim(); value = body[(colon + 1)..].Trim(); }
                else
                {
                    int sp = body.IndexOf(' ');
                    if (sp >= 0) { name = body[..sp].Trim(); value = body[(sp + 1)..].Trim(); }
                    else { name = body; value = ""; }
                }
                HandleDirective(song, name.ToLowerInvariant(), value, ref section, ref inTab);
                continue;
            }

            if (inTab)
            {
                song.Lines.Add(new ChordLine { Kind = ChordLineKind.Tab, Text = raw, Section = "tab" });
                continue;
            }

            if (trimmed.Length == 0)
            {
                song.Lines.Add(new ChordLine { Kind = ChordLineKind.Empty, Section = section });
                continue;
            }

            var segs = ParseLyricLine(raw, out bool had);
            if (had) song.HasChords = true;
            song.Lines.Add(new ChordLine { Kind = ChordLineKind.Lyric, Segments = segs, Section = section });
        }

        song.IsPlainText = !sawDirective && !song.HasChords;
        return song;
    }

    private static void HandleDirective(ChordSong song, string name, string value,
                                        ref string section, ref bool inTab)
    {
        switch (name)
        {
            case "title": case "t": song.Title = value; break;
            case "subtitle": case "st": song.Subtitle = value; break;
            case "artist": song.Artist = value; break;
            case "composer": song.Composer = value; break;
            case "key": song.Key = value; break;
            case "capo": song.Capo = value; break;
            case "tempo": song.Tempo = value; break;

            case "comment": case "c":
            case "comment_italic": case "ci":
            case "comment_box": case "cb":
                if (!string.IsNullOrWhiteSpace(value))
                    song.Lines.Add(new ChordLine { Kind = ChordLineKind.Comment, Text = value, Section = section });
                break;

            case "start_of_chorus": case "soc":
                section = "chorus";
                if (!string.IsNullOrWhiteSpace(value))
                    song.Lines.Add(new ChordLine { Kind = ChordLineKind.Section, Text = value, Section = "chorus" });
                break;
            case "end_of_chorus": case "eoc": section = ""; break;

            case "start_of_verse": case "sov":
                section = "verse";
                if (!string.IsNullOrWhiteSpace(value))
                    song.Lines.Add(new ChordLine { Kind = ChordLineKind.Section, Text = value, Section = "verse" });
                break;
            case "end_of_verse": case "eov": section = ""; break;

            case "start_of_bridge": case "sob":
                section = "bridge";
                if (!string.IsNullOrWhiteSpace(value))
                    song.Lines.Add(new ChordLine { Kind = ChordLineKind.Section, Text = value, Section = "bridge" });
                break;
            case "end_of_bridge": case "eob": section = ""; break;

            case "start_of_tab": case "sot": inTab = true; section = "tab"; break;
            case "end_of_tab": case "eot": inTab = false; section = ""; break;

            case "chorus":
                song.Lines.Add(new ChordLine
                {
                    Kind = ChordLineKind.Section,
                    Text = string.IsNullOrWhiteSpace(value) ? "Refrain" : value,
                    Section = "chorus"
                });
                break;

            // Unknown / layout directives (define, new_page, columns, …) are ignored.
            default: break;
        }
    }

    private static List<ChordSegment> ParseLyricLine(string line, out bool hadChord)
    {
        var segs = new List<ChordSegment>();
        hadChord = false;

        var lyric = new StringBuilder();
        string pendingChord = "";
        bool havePending = false;
        int i = 0;

        while (i < line.Length)
        {
            if (line[i] == '[')
            {
                int close = line.IndexOf(']', i + 1);
                if (close > i)
                {
                    if (havePending || lyric.Length > 0)
                    {
                        segs.Add(new ChordSegment { Chord = pendingChord, Lyric = lyric.ToString() });
                        lyric.Clear();
                    }
                    pendingChord = line.Substring(i + 1, close - i - 1);
                    havePending = true;
                    hadChord = true;
                    i = close + 1;
                    continue;
                }
            }
            lyric.Append(line[i]);
            i++;
        }

        if (havePending || lyric.Length > 0)
            segs.Add(new ChordSegment { Chord = pendingChord, Lyric = lyric.ToString() });
        if (segs.Count == 0)
            segs.Add(new ChordSegment());
        return segs;
    }
}

/// <summary>
/// Transposes chord symbols by a number of semitones. Recognises a root note
/// (A–G with optional #/b) plus any suffix and an optional "/bass" note; anything
/// that is not a chord (e.g. "N.C.") is returned unchanged.
/// </summary>
public static class ChordTransposer
{
    private static readonly string[] Sharp = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
    private static readonly string[] Flat  = { "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B" };

    public static string Transpose(string chord, int semitones, bool preferFlats)
    {
        if (string.IsNullOrWhiteSpace(chord)) return chord;
        int shift = ((semitones % 12) + 12) % 12;
        if (shift == 0) return chord;

        var parts = chord.Split('/');
        for (int p = 0; p < parts.Length; p++)
            parts[p] = TransposeToken(parts[p], shift, preferFlats);
        return string.Join("/", parts);
    }

    private static string TransposeToken(string token, int shift, bool preferFlats)
    {
        if (token.Length == 0) return token;
        char root = char.ToUpperInvariant(token[0]);
        if (root < 'A' || root > 'G') return token;   // not a chord root

        int idx = NoteIndex(root);
        int consumed = 1;
        if (token.Length > 1)
        {
            if (token[1] == '#') { idx += 1; consumed = 2; }
            else if (token[1] == 'b') { idx -= 1; consumed = 2; }
        }
        idx = ((idx % 12) + 12) % 12;
        int ni = (idx + shift) % 12;
        return (preferFlats ? Flat[ni] : Sharp[ni]) + token[consumed..];
    }

    private static int NoteIndex(char c) => c switch
    {
        'C' => 0, 'D' => 2, 'E' => 4, 'F' => 5, 'G' => 7, 'A' => 9, 'B' => 11, _ => 0
    };
}
