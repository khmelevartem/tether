# Windows shows a stale .exe icon after a jpackage reinstall

After installing a freshly packaged Windows build (`packageMsi`) over a path that already held an earlier Tether install, Explorer can keep showing the **old** launcher icon (e.g. the Compose/Kotlin default from a build made before `windows { iconFile }` was wired). The packaged `.exe` is correct — this is the Windows shell icon cache, keyed by file path, serving a stale entry.

Do not trust the eyeball in Explorer. Verify the icon actually embedded in the binary with the same API the shell uses, `PrivateExtractIcons` (extract a frame to PNG and look):

```powershell
Add-Type -AssemblyName System.Drawing
# render the 256px frame embedded in the installed exe
$ico = [System.Drawing.Icon]::ExtractAssociatedIcon("C:\Program Files\Tether\Tether.exe")
# ...or PrivateExtractIcons for an exact per-size frame; see git history of this note.
```

If the embedded icon is right but Explorer is wrong, it's the cache. Clear it:

```powershell
ie4uinit.exe -show
Stop-Process -Name explorer -Force
Remove-Item "$env:LOCALAPPDATA\IconCache.db" -Force -ErrorAction SilentlyContinue
Remove-Item "$env:LOCALAPPDATA\Microsoft\Windows\Explorer\iconcache*.db" -Force -ErrorAction SilentlyContinue
Start-Process explorer
```

Or copy the exe to a path the shell has never cached — it renders the correct icon there immediately.

**First-time installs are unaffected**: with no prior cache entry for the path, the shell extracts the icon fresh. This bites repeated dev reinstalls at the same path, not end users. There is no Compose/jpackage switch that rebuilds an already-poisoned shell cache; mitigate by bumping `packageVersion` (a clean major upgrade replaces the file) or uninstalling before reinstalling.
