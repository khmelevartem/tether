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
        val validated = DeviceNameValidator.validate(value)
        return validated.fold(
            onSuccess = { trimmed ->
                runCatching { persistence.write(trimmed) }
                    .fold(
                        onSuccess = {
                            _name.value = trimmed
                            Result.success(trimmed)
                        },
                        onFailure = { Result.failure(it) },
                    )
            },
            onFailure = { Result.failure(it) },
        )
    }
}
