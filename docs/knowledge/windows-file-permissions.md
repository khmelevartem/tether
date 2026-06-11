# Device private key — owner-only permissions across platforms

The device identity private key (`device_private.key`) is restricted to the file owner on every desktop platform, but the underlying permission model differs by filesystem.

On Unix (Linux / macOS) the file carries POSIX `600`: owner read/write, no group or other access.

On Windows (NTFS) there is no POSIX permission model — restriction goes through the file's access-control list instead. The whole ACL is replaced with a single ALLOW entry for the file owner, covering the file-access rights a private key needs (read, write, delete, attribute and ACL access) but not execute. Replacing the list — rather than appending — is what drops the default and inherited entries that would otherwise grant `Everyone`, `BUILTIN\Users` and `Authenticated Users` access.

On a filesystem that exposes neither model the key cannot be narrowed; the restriction step logs a warning and leaves the file as-is so the gap stays observable.
