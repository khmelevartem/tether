package com.tubetoast.tether.discovery

import com.tubetoast.tether.config.DeviceNameStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

fun CoroutineScope.republishOnNameChange(store: DeviceNameStore, discovery: DeviceDiscovery): Job =
    launch {
        store.name
            .drop(1)
            .collect { discovery.republish(it) }
    }
