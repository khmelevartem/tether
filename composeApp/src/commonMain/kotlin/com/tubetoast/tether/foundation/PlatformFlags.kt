package com.tubetoast.tether.foundation

expect val IsPickerModeChooserNeeded: Boolean

/** True only on iOS: received media should offer a "Save to Photos" toggle in Settings. */
expect val IsGalleryToggleShown: Boolean
