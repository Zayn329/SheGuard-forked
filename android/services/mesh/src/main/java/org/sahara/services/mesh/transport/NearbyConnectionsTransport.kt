package org.sahara.services.mesh.transport

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Real Google Nearby Connections byte transport. */
class NearbyConnectionsTransport(
    context: Context,
    private val serviceId: String = context.packageName
) : MeshTransport {
    private val appContext = context.applicationContext
    private val client = Nearby.getConnectionsClient(appContext)
    private val strategy = Strategy.P2P_CLUSTER
    private val localName = "SheGuard-${serviceId.substringAfterLast('.').take(12)}"
    private val _status = MutableStateFlow(MeshTransportStatus.STOPPED)
    private val _peers = MutableStateFlow<List<MeshPeer>>(emptyList())
    private val _incomingPayloads = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    private val connectedEndpoints = LinkedHashSet<String>()

    override val status: StateFlow<MeshTransportStatus> = _status.asStateFlow()
    override val peers: StateFlow<List<MeshPeer>> = _peers.asStateFlow()
    override val incomingPayloads: Flow<ByteArray> = _incomingPayloads.asSharedFlow()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { _incomingPayloads.tryEmit(it) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS && connectedEndpoints.isNotEmpty()) {
                _status.value = MeshTransportStatus.CONNECTED
            }
        }
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { _status.value = MeshTransportStatus.ERROR }
        }

        override fun onConnectionResult(endpointId: String, result: com.google.android.gms.nearby.connection.ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpoints.add(endpointId)
                _status.value = MeshTransportStatus.CONNECTED
                publishPeers()
            } else {
                _status.value = MeshTransportStatus.DISCONNECTED
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            publishPeers()
            _status.value = if (connectedEndpoints.isEmpty()) {
                MeshTransportStatus.DISCONNECTED
            } else {
                MeshTransportStatus.CONNECTED
            }
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: com.google.android.gms.nearby.connection.DiscoveredEndpointInfo) {
            _peers.value = (_peers.value + MeshPeer(endpointId, info.endpointName)).distinctBy { it.endpointId }
            if (endpointId !in connectedEndpoints) {
                connect(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _peers.value = _peers.value.filterNot { it.endpointId == endpointId }
        }
    }

    override fun startAdvertising(): MeshTransportResult = runCatching {
        if (!MeshPermissionManager.hasAllPermissions(appContext)) {
            _status.value = MeshTransportStatus.PERMISSION_REQUIRED
            return MeshTransportResult.Rejected("Nearby permissions are required")
        }
        _status.value = MeshTransportStatus.STARTING
        client.startAdvertising(
            localName,
            serviceId,
            lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener { _status.value = MeshTransportStatus.ADVERTISING }
            .addOnFailureListener { _status.value = MeshTransportStatus.ERROR }
        MeshTransportResult.Accepted
    }.getOrElse { error ->
        _status.value = if (error is SecurityException) MeshTransportStatus.PERMISSION_REQUIRED else MeshTransportStatus.ERROR
        MeshTransportResult.Rejected(error.message ?: "Unable to start advertising")
    }

    override fun startDiscovery(): MeshTransportResult = runCatching {
        if (!MeshPermissionManager.hasAllPermissions(appContext)) {
            _status.value = MeshTransportStatus.PERMISSION_REQUIRED
            return MeshTransportResult.Rejected("Nearby permissions are required")
        }
        _status.value = MeshTransportStatus.STARTING
        client.startDiscovery(
            serviceId,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener { _status.value = MeshTransportStatus.DISCOVERING }
            .addOnFailureListener { _status.value = MeshTransportStatus.ERROR }
        MeshTransportResult.Accepted
    }.getOrElse { error ->
        _status.value = if (error is SecurityException) MeshTransportStatus.PERMISSION_REQUIRED else MeshTransportStatus.ERROR
        MeshTransportResult.Rejected(error.message ?: "Unable to start discovery")
    }

    override fun connect(endpointId: String): MeshTransportResult = runCatching {
        if (!MeshPermissionManager.hasAllPermissions(appContext)) {
            _status.value = MeshTransportStatus.PERMISSION_REQUIRED
            return MeshTransportResult.Rejected("Nearby permissions are required")
        }
        _status.value = MeshTransportStatus.CONNECTING
        client.requestConnection(localName, endpointId, lifecycleCallback)
            .addOnFailureListener { _status.value = MeshTransportStatus.ERROR }
        MeshTransportResult.Accepted
    }.getOrElse { error ->
        _status.value = if (error is SecurityException) MeshTransportStatus.PERMISSION_REQUIRED else MeshTransportStatus.ERROR
        MeshTransportResult.Rejected(error.message ?: "Unable to request connection")
    }

    override fun send(endpointId: String, payload: ByteArray): MeshTransportResult = runCatching {
        if (!MeshPermissionManager.hasAllPermissions(appContext)) {
            _status.value = MeshTransportStatus.PERMISSION_REQUIRED
            return MeshTransportResult.Rejected("Nearby permissions are required")
        }
        if (endpointId !in connectedEndpoints) {
            return MeshTransportResult.Rejected("Peer is not connected")
        }
        client.sendPayload(endpointId, Payload.fromBytes(payload))
            .addOnFailureListener { _status.value = MeshTransportStatus.DISCONNECTED }
        MeshTransportResult.Accepted
    }.getOrElse { error ->
        _status.value = if (error is SecurityException) MeshTransportStatus.PERMISSION_REQUIRED else MeshTransportStatus.ERROR
        MeshTransportResult.Rejected(error.message ?: "Unable to send payload")
    }

    override fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connectedEndpoints.clear()
        _peers.value = emptyList()
        _status.value = MeshTransportStatus.STOPPED
    }

    private fun publishPeers() {
        _peers.value = _peers.value.filter { it.endpointId in connectedEndpoints }
    }
}
