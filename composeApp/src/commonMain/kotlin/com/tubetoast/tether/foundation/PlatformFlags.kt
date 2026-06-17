package com.tubetoast.tether.foundation

expect val IsPickerModeChooserNeeded: Boolean

/** Controls whether Settings shows a "Save to Photos" toggle that routes received photos and videos into the system photo library instead of keeping them only in the app's file area. */
expect val IsGalleryToggleShown: Boolean

/** True on iOS, macOS, and Desktop JVM; false on Android. Top-bar titles are centered on non-Android platforms per platform idiom. */
expect val IsTopBarTitleCentered: Boolean
