package com.tubetoast.tether.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class DeviceNameStore(
    private val persistence: DeviceNamePersistence,
) {
    private val _name = MutableStateFlow("")

    val name: Flow<String> get() = _name

    suspend fun init() {
        _name.value = runCatching { persistence.read() }.getOrNull() ?: defaultDeviceName()
    }

    suspend fun setName(value: String): Result<String> {
        val trimmed = DeviceNameValidator.validate(value).getOrElse { return Result.failure(it) }
        return runCatching { persistence.write(trimmed) }.map {
            _name.value = trimmed
            trimmed
        }
    }
}
