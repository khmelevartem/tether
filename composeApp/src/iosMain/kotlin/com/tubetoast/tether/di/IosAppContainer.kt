package com.tubetoast.tether.di

import com.tubetoast.tether.share.SharedPendingFilesReader
import platform.Foundation.NSFileManager

// Must match appGroupID in TetherShareExtension/ShareViewController.swift
private const val APP_GROUP_ID = "group.com.tubetoast.tether"

open class IosAppContainer(
    config: IosAppConfig,
) : AppleAppContainer(config) {
    internal open val sharedPendingFilesReader: SharedPendingFilesReader? by lazy {
        val container = NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)
            ?.path ?: return@lazy null
        SharedPendingFilesReader(
            inboxDir = "$container/inbox",
            stagingDir = "$container/staging",
        )
    }
}
