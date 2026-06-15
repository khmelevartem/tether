package com.tubetoast.tether.di

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Owns the reference to the hosting ComposeUIViewController and resolves the topmost presenter
 * for modal presentation (document/photo pickers).
 *
 * Presenting from the hosting VC's window root is reliable; connectedScenes is a last resort
 * because SwiftUI-managed scenes do not always expose a key window at pick time.
 */
class IosViewControllerHolder {
    private var hostViewController: UIViewController? = null

    fun attach(vc: UIViewController) {
        hostViewController = vc
    }

    fun topmostPresenter(): UIViewController? {
        val root = hostViewController?.view?.window?.rootViewController
            ?: hostViewController
            ?: connectedScenesRoot()
        var top = root
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        return top
    }

    private fun connectedScenesRoot(): UIViewController? =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstNotNullOfOrNull { scene ->
                scene.keyWindow
                    ?: scene.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }
            }?.rootViewController
}
