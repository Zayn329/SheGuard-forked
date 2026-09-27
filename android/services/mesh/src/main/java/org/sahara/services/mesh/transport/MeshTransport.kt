package org.sahara.services.mesh.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class MeshTransportStatus {
    STOPPED,
    STARTING,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    ERROR
}

data class MeshPeer(
    val endpointId: String,
    val name: String
)

sealed class MeshTransportResult {
    object Accepted : MeshTransportResult()
    data class Rejected(val reason: String) : MeshTransportResult()
}

/** Transport boundary used by the SheGuard adapter and by deterministic tests. */
interface MeshTransport {
    val status: StateFlow<MeshTransportStatus>
    val peers: StateFlow<List<MeshPeer>>
    val incomingPayloads: Flow<ByteArray>

    fun startAdvertising(): MeshTransportResult
    fun startDiscovery(): MeshTransportResult
    fun connect(endpointId: String): MeshTransportResult
    fun send(endpointId: String, payload: ByteArray): MeshTransportResult
    fun stop()
}
