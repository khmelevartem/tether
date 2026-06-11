# jpackage strips reflectively-loaded JDK modules from the desktop runtime

`./gradlew run` launches the desktop app on the full system JDK, so every JDK module is present. `packageDistributionForCurrentOS` (Dmg / Msi / Deb) instead bundles a **jlinked** runtime image inside the installer, containing only the modules jlink detects. Modules the app reaches **reflectively** are absent, so the packaged app crashes at startup or first use where `run` worked. Always verify the packaged artifact, not just `run`.

Symptoms seen here:
- `NoClassDefFoundError: sun/misc/Unsafe` at startup — DataStore's bundled protobuf needs `jdk.unsupported`.
- `NoSuchAlgorithmException` for `KeyPairGenerator.getInstance("EC")` — the SunEC provider lives in `jdk.crypto.ec`, resolved by name through JCA.

Fix: list the needed modules explicitly in `compose.desktop { application { nativeDistributions { modules(...) } } }` (`composeApp/build.gradle.kts`).

Finding the set:
- `./gradlew :composeApp:suggestModules` runs jdeps (static bytecode analysis) and reports modules referenced **directly** — here `java.instrument`, `java.management`, `jdk.unsupported`. Copy its output into `modules(...)`; it is advisory, Compose does not apply it automatically.
- jdeps cannot see modules loaded **by string** (`getInstance("EC")`, `ServiceLoader`, `Class.forName`). Those come from knowing what the app uses — `jdk.crypto.ec` (EC device keys) is the one `suggestModules` misses.
