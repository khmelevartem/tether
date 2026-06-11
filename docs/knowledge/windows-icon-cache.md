# Windows shows a stale .exe icon after a jpackage reinstall

After installing a freshly packaged Windows build (`packageMsi`) over a path that already held an earlier Tether install, Explorer can keep showing the **old** launcher icon. The packaged `.exe` is correct — this is the Windows shell icon cache, which keys cached icons by file path and serves a stale entry for a path it has seen before.

Don't trust the eyeball in Explorer. Confirm the icon actually embedded in the binary by reading the icon resource straight from the PE:

```powershell
Add-Type -AssemblyName System.Drawing
[System.Drawing.Icon]::ExtractAssociatedIcon("C:\Program Files\Tether\Tether.exe").ToBitmap().Save("$env:TEMP\tether-icon.png")
```

`ExtractAssociatedIcon` reads the icon resource from the executable itself, not from the cache — so a correct PNG here means the binary is fine and the cache is stale. (The shell's own lookup goes through `SHGetFileInfo` and the system image list, a different path — see [SHGetFileInfo](https://learn.microsoft.com/windows/win32/api/shellapi/nf-shellapi-shgetfileinfow).)

If the embedded icon is right but Explorer is wrong, clear the per-user icon cache and restart Explorer:

```powershell
Stop-Process -Name explorer -Force
Remove-Item "$env:LOCALAPPDATA\Microsoft\Windows\Explorer\iconcache*.db" -Force -ErrorAction SilentlyContinue
Start-Process explorer
```

On Windows 10/11 the icon databases live under `%LOCALAPPDATA%\Microsoft\Windows\Explorer\iconcache_*.db` (the legacy `%LOCALAPPDATA%\IconCache.db` of Windows 7/8 no longer exists). `ie4uinit.exe -show` also nudges the shell to refresh, but the switch is undocumented — treat it as a convenience, not a guaranteed interface.

Or copy the `.exe` to a path the shell has never cached — it renders the correct icon there immediately.

**First-time installs are unaffected**: with no prior cache entry for the path, the shell extracts the icon fresh. This bites repeated dev reinstalls at the same path, not end users.
