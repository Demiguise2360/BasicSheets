namespace BasicSheets.Shared.Services;

/// <summary>
/// Pure geometry for the manual crop editor: given the handle being dragged and the
/// pointer position (as 0–1 fractions of the page), produce the new crop rectangle.
/// Edge/corner handles set the dragged edges to the pointer; "body" translates the whole
/// rectangle. A minimum size is always preserved and the rectangle stays within [0,1].
/// </summary>
public static class CropEditMath
{
    public const double MinSize = 0.08;

    public static (double L, double T, double R, double B) Apply(
        string handle,
        (double L, double T, double R, double B) start,
        double startFx, double startFy, double fx, double fy,
        double min = MinSize)
    {
        if (handle == "body")
        {
            double w = start.R - start.L, h = start.B - start.T;
            double nl = Clamp(start.L + (fx - startFx), 0, 1 - w);
            double nt = Clamp(start.T + (fy - startFy), 0, 1 - h);
            return (nl, nt, nl + w, nt + h);
        }

        double l = start.L, t = start.T, r = start.R, b = start.B;
        if (handle.Contains('w')) l = Clamp(Math.Min(fx, r - min), 0, 1);
        if (handle.Contains('e')) r = Clamp(Math.Max(fx, l + min), 0, 1);
        if (handle.Contains('n')) t = Clamp(Math.Min(fy, b - min), 0, 1);
        if (handle.Contains('s')) b = Clamp(Math.Max(fy, t + min), 0, 1);
        return (l, t, r, b);
    }

    private static double Clamp(double v, double lo, double hi) => Math.Max(lo, Math.Min(hi, v));
}
