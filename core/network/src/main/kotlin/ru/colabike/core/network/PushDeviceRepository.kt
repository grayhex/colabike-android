package ru.colabike.core.network

import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.PersonalApi
import ru.colabike.api.models.PushDevice as PushDeviceDto
import ru.colabike.api.models.PushDeviceRegistration as RegistrationDto
import ru.colabike.core.model.DataError
import ru.colabike.core.model.PushDeviceBinding
import ru.colabike.core.model.PushDeviceRegistration
import ru.colabike.core.model.PushDeviceRepository

/**
 * The registry of push addresses (cola#342). The session decides whose phone it is, so nothing but
 * the install, the project and the address goes in the body, and the address is not echoed back.
 */
class NetworkPushDeviceRepository(
    private val api: PersonalApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PushDeviceRepository {
    override suspend fun current(): PushDeviceBinding? =
        try {
            apiCall(dispatcher) { api.getPushDevice() }.toModel()
        } catch (_: DataError.NotFound) {
            null
        }

    override suspend fun register(registration: PushDeviceRegistration): PushDeviceBinding {
        val body =
            RegistrationDto(
                installationId = UUID.fromString(registration.installationId),
                provider = RegistrationDto.Provider.rustore,
                projectId = registration.projectId,
                token = registration.token,
                expectedGeneration = registration.expectedGeneration,
            )
        return apiCall(dispatcher) { api.registerPushDevice(body) }.toModel()
    }

    override suspend fun revoke() {
        apiCall(dispatcher) { api.revokePushDevice() }
    }
}

private fun PushDeviceDto.toModel() =
    PushDeviceBinding(
        provider = provider.value,
        projectId = projectId,
        generation = generation,
        registeredAt = registeredAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        lastSeenAt = lastSeenAt.toInstant(),
    )
