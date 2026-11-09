# Android signing & seamless updates

Android refuses to install an update when the new APK is **signed with a different
key** than the copy already on the phone ("App not installed", "package conflicts
with an existing package", or a signature‑mismatch warning). Because BasicSheets
updates itself by downloading `basicsheets.apk` and handing it to the system
installer, every APK you publish **must be signed with the same key**.

Previously no signing key was configured, so builds weren't guaranteed to share a
signature — that's the warning you saw. This is now fixed with a **permanent
release keystore**.

## What was set up

- **`BasicSheets/basicsheets-release.keystore`** — your permanent signing key
  (RSA‑2048, ~30‑year validity, alias `basicsheets`). **Git‑ignored.**
- **`BasicSheets/signing.local.props`** — holds the keystore path + passwords and
  turns on signing for `Release` Android builds. **Git‑ignored.**
- **`BasicSheets/signing.local.props.sample`** — committed template (no secrets).
- **`BasicSheets.csproj`** imports `signing.local.props` when it exists; if it's
  missing, Release builds are simply unsigned (nothing breaks).

> ⚠️ **Back up `basicsheets-release.keystore` and its password** (password manager
> + an offline copy). If you ever lose them you can **never** ship an update that
> installs over the current app again — every user would have to uninstall and
> lose their data. The password is in `signing.local.props`.

## Building a signed update APK

```bash
dotnet publish BasicSheets/BasicSheets.csproj -f net9.0-android -c Release -p:AndroidPackageFormat=apk
```

The signed APK is written under
`BasicSheets/bin/Release/net9.0-android/publish/` (e.g.
`com.companyname.basicsheets-Signed.apk`). For each release:

1. **Bump the version** in `BasicSheets.csproj`:
   - `ApplicationVersion` (the Android *versionCode*) — **must increase every
     release**; the in‑app update check compares this number.
   - `ApplicationDisplayVersion` (the name users see, e.g. `1.1.4`).
2. Build with the command above.
3. Upload the APK to Netlify as `assets/basicsheets.apk`, and update
   `assets/version.json` (`version`, `versionCode`, `changelog`) to match.

Confirm the signature any time with:

```bash
# apksigner lives in the Android SDK build-tools
apksigner verify --print-certs BasicSheets/bin/Release/net9.0-android/publish/com.companyname.basicsheets-Signed.apk
```

## One‑time migration (important)

The app currently on your phone was signed with a *different* key (or none), so the
**first** release‑signed update will **not** install over it — Android will block it
as a signature mismatch. You need one clean reinstall, **once**:

1. In the app, open the ⊞ menu → **Notizen‑Backup → "In Datei sichern"** to save your
   pen/marker/text annotations (these live only in the app and are wiped on
   uninstall). Also note any PDFs you imported — app data is cleared on uninstall,
   so you may need to re‑import them.
2. **Uninstall** BasicSheets.
3. Install the new **release‑signed** APK.
4. Reopen the app and **Notizen‑Backup → "Aus Datei laden"** to restore your notes;
   re‑import PDFs if needed.

After this one reinstall, **every future update installs cleanly** with no
signature warning, because they're all signed with the same keystore.

## Prompts that remain (these are normal, not a bug)

Sideloaded apps (not from the Play Store) always show some system UI you can't
remove:

- **"Allow this app to install unknown apps"** — a one‑time permission per source.
  Grant it to BasicSheets once (Settings → Apps → BasicSheets → *Install unknown
  apps*) and it won't ask again.
- **"Do you want to update this app?"** — the system installer's confirmation on
  every update. Unavoidable without the Play Store or a managed (device‑owner)
  installer.
- **Google Play Protect** may show a "scan / unknown developer" notice for apps
  installed outside Play. A consistent real signing key (now in place) reduces it,
  but it can't be fully removed for sideloaded apps.

Only the **signature‑mismatch failure** is a real defect, and that's what the
keystore fixes.

## Optional: fix the package id

The app id is still the template default `com.companyname.basicsheets`. If you want
a real one (e.g. `ch.desgin.basicsheets`), change `ApplicationId` in
`BasicSheets.csproj` — but note that changing it makes Android treat it as a **new
app** (another reinstall). The one‑time reinstall above is the natural moment to do
this too, if you want.

## Moving to another build machine

Copy both `basicsheets-release.keystore` and `signing.local.props` into
`BasicSheets/` on the other machine (they're git‑ignored, so they don't travel with
the repo). Same key in → identical signature out.
