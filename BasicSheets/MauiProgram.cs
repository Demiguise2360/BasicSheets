using BasicSheets.Services;
using BasicSheets.Shared.Services;
using BasicSheets.Shared.Data;
using Microsoft.Extensions.Logging;

namespace BasicSheets
{
    public static class MauiProgram
    {
        public static MauiApp CreateMauiApp()
        {
            var builder = MauiApp.CreateBuilder();
            builder
                .UseMauiApp<App>()
                .ConfigureFonts(fonts =>
                {
                    fonts.AddFont("Tw Cen MT.ttf", "TwCenMT");
                });
            // Add device-specific services used by the BasicSheets.Shared project
            builder.Services.AddSingleton<IFormFactor, FormFactor>();
            builder.Services.AddSingleton<BasicSheetsDb>();
            builder.Services.AddSingleton<SheetService>();
            builder.Services.AddSingleton<SetlistService>();
            builder.Services.AddSingleton<CollectionService>();
            builder.Services.AddSingleton<ThumbnailService>();
            builder.Services.AddSingleton<SettingsService>();
            builder.Services.AddSingleton<BookmarkService>();
            builder.Services.AddSingleton<ViewerButtonService>();
            builder.Services.AddSingleton<IUpdateService, UpdateService>();
            builder.Services.AddSingleton<IFilePickerService, NativeFilePickerService>();

            builder.Services.AddMauiBlazorWebView();

#if DEBUG
            builder.Services.AddBlazorWebViewDeveloperTools();
            builder.Logging.AddDebug();
#endif

            return builder.Build();
        }
    }
}
