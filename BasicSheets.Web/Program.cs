using BasicSheets.Shared.Services;
using BasicSheets.Shared.Data;
using BasicSheets.Web.Components;
using BasicSheets.Web.Services;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

builder.Services.AddSignalR(o => o.MaximumReceiveMessageSize = 10 * 1024 * 1024);

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

var app = builder.Build();

// Configure the HTTP request pipeline.
if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error", createScopeForErrors: true);
    // The default HSTS value is 30 days. You may want to change this for production scenarios, see https://aka.ms/aspnetcore-hsts.
    app.UseHsts();
}

app.UseHttpsRedirection();

app.UseStaticFiles();
app.UseAntiforgery();

app.MapRazorComponents<App>()
    .AddInteractiveServerRenderMode()
    .AddAdditionalAssemblies(typeof(BasicSheets.Shared._Imports).Assembly);

app.Run();
