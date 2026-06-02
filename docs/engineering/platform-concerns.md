# Platform Concerns — Recurring Stumbling Points

Concrete checks to apply against every PR that touches a platform source set
(`androidMain/`, `iosMain/`, `appleMain/`, `jvmMain/`, `desktopMain/`,
`commonMain/` with `expect`, `iosApp/`, `AndroidManifest.xml`, or build
configuration affecting targets).

This list is for verifying **specific things we've already been bitten by**.
The general "by the spirit" platform audit — surfacing the platform assumptions
a change embeds and where they may not hold — sits in
[review-platform.md](../../.claude/agents/review-platform.md) and runs first.
A clean checklist here does not imply a clean platform pass.

## Checklist

1. **Source set placement.** Code in `androidMain/` AND `desktopMain/` doing the
   same thing → `jvmMain` candidate. Code in any platform set with no platform
   API call → `commonMain` candidate. Apple-specific code that today only iOS
   uses → `appleMain` only if plausibly shared with a future Apple-native
   target; otherwise `iosMain`. Rule: [architecture-principles.md](architecture-principles.md),
   [modules.md](modules.md).

2. **expect/actual completeness.** Every `expect` in `commonMain` has matching
   `actual` for every target. Verify via
   `rg "^expect (fun|class|object|val|interface)" composeApp/src/commonMain`,
   then for each touched declaration confirm every target source set
   (`androidMain` OR `jvmMain` parent; `iosMain` OR `appleMain` parent;
   `desktopMain` OR `jvmMain` parent) supplies `actual`.

3. **Android API level guards.** New Android API call → `Build.VERSION.SDK_INT`
   guard. Deprecated API → `@Suppress("DEPRECATION")` + fallback. New permission
   → entry in `AndroidManifest.xml` (network, location, camera, FGS types).

4. **Android foreground service.** Any change to FGS lifecycle → Android 14+
   FGS type declaration and timeout behaviour. Reference:
   [android-fgs.md](../knowledge/android-fgs.md).

5. **Apple ObjC delegate retention.** Every `.delegate =` assignment on an ObjC
   property has a corresponding Kotlin strong reference (class field, not
   local). Reference: [apple-platform.md — ObjC delegate GC](../knowledge/apple-platform.md#objc-delegate-gc-silent-callback-loss).

6. **iOS Local Network Privacy.** Feature uses mDNS / Bonjour / local network →
   `iosApp/iosApp/Info.plist` declares `NSLocalNetworkUsageDescription` and
   `NSBonjourServices`. Reference:
   [apple-platform.md — Local Network Privacy](../knowledge/apple-platform.md#ios-14-local-network-privacy).

7. **Apple Keychain query dicts.** Any `SecItem*` call uses
   `CFDictionaryCreateMutable` + `CFDictionarySetValue` directly, not
   `NSMutableDictionary` bridged via `CFBridgingRetain(...) as CFDictionaryRef`.
   Reference: [apple-platform.md — Keychain query dicts](../knowledge/apple-platform.md#keychain-query-dicts-must-be-built-via-cfmutabledictionary).

8. **Apple ARC ↔ CF refcount.** Every `CFBridgingRetain(...)` (+1) paired with
   exactly one `CFRelease` of the resulting CF pointer after the receiving CF
   container has taken its own retain. Every `CFDictionaryCreateMutable` /
   `SecKeyCreateRandomKey` / `SecItemCopyMatching` out-ref paired with
   `CFRelease` on the success path.

9. **Typed-but-loosely-validated SDK configuration.** Platform-SDK configuration
   expressed as a string-keyed dictionary, option struct, or attribute-flag
   list (Apple `kSec*` / `kCF*` placement and NSURLSession config dicts;
   Android `MediaCodec` keys; JVM `KeyStore` options): the compiler validates
   only the type-erased dictionary shape. Misplaced-but-typeable attributes
   are silently ignored at runtime — they neither break compilation nor
   surface in unit tests that don't reach the runtime path. Verify each key's
   placement and applicability against the platform's authoritative SDK
   reference for the specific call (which dict, which method overload, query
   vs creation), not by inference from the call site.

10. **Platform parity / regression risk.** Feature lands on one platform → open
    issue or stub for the others. Change must not break the build for any
    target (`applyHierarchyTemplate` consequences).

11. **Assertive live region re-announcement.** A live region announces only when
    the node's text content changes; identical copy rendered again is silent to
    TalkBack / VoiceOver. Re-announcing the *same* string on a repeat trigger
    needs the platform bypass (`View.announceForAccessibility` on Android,
    `UIAccessibility.post(.announcement,)` on iOS), not a `semantics` property.
    Verify against the
    [Compose LiveRegionMode reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/semantics/LiveRegionMode).
