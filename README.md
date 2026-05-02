This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM), and macOS.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that's common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple's CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you're sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE's toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Debug CLI Runner (JVM)

A headless Ktor + mDNS debug runner. Starts a `FileServer` and `MdnsDiscovery` stub and prints discovered peers.

- on macOS/Linux
  ```shell
  # default: random port, device name = "Tether-$USER"
  ./gradlew :composeApp:run

  # custom name and port
  ./gradlew :composeApp:run --args="--name MyMac --port 8080"
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run --args="--name MyPC --port 8080"
  ```

Once running, verify the server at: `http://localhost:{port}/health` → `Tether OK`

Press `Ctrl+C` to stop.

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE's toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Build and Run macOS Application

> Requires Apple Silicon Mac (M1+). The `org.jetbrains.compose.experimental.macos.enabled=true`
> property in `gradle.properties` must be set (already included in this project).

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:runReleaseExecutableMacosArm64
  ```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
