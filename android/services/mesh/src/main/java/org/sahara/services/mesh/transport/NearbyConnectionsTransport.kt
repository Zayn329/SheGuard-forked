package org.sahara.services.mesh.transport

import android.content.Context
import com.google.android.gms.common.api.ApiException
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
import org.sahara.services.mesh.util.MeshLogger
import java.util.concurrent.ConcurrentHashMap

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
    private val endpointNames = ConcurrentHashMap<String, String>()

    @Volatile
    private var isAdvertising = false

    @Volatile
    private var isDiscovering = false

    init {
        MeshLogger.i("MESH_INITIALIZED: serviceId=$serviceId, localName=$localName, strategy=P2P_CLUSTER")
    }

    override val status: StateFlow<MeshTransportStatus> = _status.asStateFlow()
    override val peers: StateFlow<List<MeshPeer>> = _peers.asStateFlow()
    override val incomingPayloads: Flow<ByteArray> = _incomingPayloads.asSharedFlow()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                MeshLogger.i("MESSAGE_RECEIVED: payloadSize=${bytes.size} bytes from endpoint=$endpointId")
                _incomingPayloads.tryEmit(bytes)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS && connectedEndpoints.isNotEmpty()) {
                updateStatus()
            }
        }
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            endpointNames[endpointId] = connectionInfo.endpointName
            MeshLogger.i("CONNECTION_INITIATED: endpointId=$endpointId, name=${connectionInfo.endpointName}")
            client.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    MeshLogger.i("CONNECTION_ACCEPTED: endpointId=$endpointId")
                }
                .addOnFailureListener { e ->
                    MeshLogger.e("CONNECTION_ACCEPT_FAILED: endpointId=$endpointId, ${formatError(e)}", e)
                    updateStatusAfterFailure()
                }
        }

        override fun onConnectionResult(endpointId: String, result: com.google.android.gms.nearby.connection.ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpoints.add(endpointId)
                publishPeers()
                updateStatus()
                val peerName = endpointNames[endpointId] ?: "Unknown"
                MeshLogger.i("CONNECTION_ESTABLISHED: endpointId=$endpointId, peerName=$peerName, totalConnected=${connectedEndpoints.size}")
            } else {
                connectedEndpoints.remove(endpointId)
                publishPeers()
                updateStatusAfterFailure()
                val statusStr = ConnectionsStatusCodes.getStatusCodeString(result.status.statusCode)
                MeshLogger.w("CONNECTION_FAILED: endpointId=$endpointId, statusCode=${result.status.statusCode} ($statusStr), statusMessage=${result.status.statusMessage}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            publishPeers()
            updateStatus()
            MeshLogger.i("PEER_DISCONNECTED: endpointId=$endpointId, remainingConnected=${connectedEndpoints.size}")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: com.google.android.gms.nearby.connection.DiscoveredEndpointInfo) {
            endpointNames[endpointId] = info.endpointName
            MeshLogger.i("PEER_DISCOVERED: endpointId=$endpointId, name=${info.endpointName}")
            if (endpointId !in connectedEndpoints) {
                MeshLogger.i("CONNECTION_REQUESTED: Initiating connection to discovered endpoint=$endpointId")
                connect(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            MeshLogger.i("PEER_LOST: endpointId=$endpointId")
            endpointNames.remove(endpointId)
            if (endpointId in connectedEndpoints) {
                connectedEndpoints.remove(endpointId)
                publishPeers()
                updateStatus()
            }
        }
    }

    override fun startAdvertising(): MeshTransportResult = runCatching {
        if (!MeshPermissionManager.hasAllPermissions(appContext)) {
            _status.value = MeshTransportStatus.PERMISSION_REQUIRED
            MeshLogger.w("ADVERTISING_FAILED: Permissions missing")
            return MeshTransportResult.Rejected("Nearby permissions are required")
        }
        if (isAdvertising) {
            MeshLogger.i("ADVERTISING_ALREADY_ACTIVE: serviceId=$serviceId")
            return MeshTransportResult.Accepted
        }
        if (connectedEndpoints.isEmpty()) {
            _status.value = MeshTransportStatus.STARTING
        }
        MeshLogger.i("ADVERTISING_STARTING: localName=$localName, serviceId=$serviceId")
        client.startAdvertising(
            localName,
            serviceId,
            lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            isAdvertising = true
            MeshLogger.i("ADVERTISING_STARTED: serviceId=$serviceId, localName=$localName")
            updateStatus()
        }.addOnFailureListener { e ->
            handleAdvertisingFailure(e)
        }
        MeshTransportResult.Accepted
    }.getOrElse { error ->
        handleAdvertisingFailure(error)
        MeshTransportResult.Rejected(error.message ?: "Unable to start advertising")
    }

    override fun startDiscovery(): MeshTransportResult = runCatching {
        if (!MeshPermissionManager.hasAllPermissions(appContext)) {
            _status.value = MeshTransportStatus.PERMISSION_REQUIRED
            MeshLogger.w("DISCOVERY_FAILED: Permissions missing")
            return MeshTransportResult.Rejected("Nearby permissions are required")
        }
        if (isDiscovering) {
            MeshLogger.i("DISCOVERY_ALREADY_ACTIVE: serviceId=$serviceId")
            return MeshTransportResult.Accepted
        }
        if (connectedEndpoints.isEmpty() && !isAdvertising) {
            _status.value = MeshTransportStatus.STARTING
        }
        MeshLogger.i("DISCOVERY_STARTING: serviceId=$serviceId")
        client.startDiscovery(
            serviceId,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            isDiscovering = true
            MeshLogger.i("DISCOVERY_STARTED: serviceId=$serviceId")
            updateStatus()
        }.addOnFailureListener { e ->
            handleDiscoveryFailure(e)
        }
        MeshTransportResult.Accepted
    }.getOrElse { error ->
        handleDiscoveryFailure(error)
        MeshTransportResult.Rejected(error.message ?: "Unable to start discovery")
    }

    override fun connect(endpointId: String): MeshTransportResult = runCatching {
        if (!MeshPermissionManager.hasAllPermissions(appContext)) {
            _status.value = MeshTransportStatus.PERMISSION_REQUIRED
            MeshLogger.w("CONNECTION_FAILED: Permissions missing to connect to endpoint=$endpointId")
            return MeshTransportResult.Rejected("Nearby permissions are required")
        }
        MeshLogger.i("CONNECTION_REQUESTED: Requesting connection to endpointId=$endpointId")
        client.requestConnection(localName, endpointId, lifecycleCallback)
            .addOnSuccessListener {
                MeshLogger.i("CONNECTION_REQUEST_SENT: endpointId=$endpointId")
            }
            .addOnFailureListener { e ->
                if (e is ApiException && e.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                    connectedEndpoints.add(endpointId)
                    publishPeers()
                    updateStatus()
                    MeshLogger.i("CONNECTION_ALREADY_CONNECTED: endpointId=$endpointId")
                } else {
                    MeshLogger.e("CONNECTION_REQUEST_FAILED: endpointId=$endpointId, ${formatError(e)}", e)
                    updateStatusAfterFailure()
                }
            }
        MeshTransportResult.Accepted
    }.getOrElse { error ->
        MeshLogger.e("CONNECTION_FAILED: endpointId=$endpointId, ${formatError(error)}", error)
        MeshTransportResult.Rejected(error.message ?: "Unable to request connection")
    }

    override fun send(endpointId: String, payload: ByteArray): MeshTransportResult = runCatching {
        if (!MeshPermissionManager.hasAllPermissions(appContext)) {
            _status.value = MeshTransportStatus.PERMISSION_REQUIRED
            MeshLogger.w("MESSAGE_SEND_FAILED: Permissions missing to send payload to endpoint=$endpointId")
            return MeshTransportResult.Rejected("Nearby permissions are required")
        }
        if (endpointId !in connectedEndpoints) {
            MeshLogger.w("MESSAGE_SEND_FAILED: Peer endpoint=$endpointId is not in connectedEndpoints")
            return MeshTransportResult.Rejected("Peer is not connected")
        }
        client.sendPayload(endpointId, Payload.fromBytes(payload))
            .addOnSuccessListener {
                MeshLogger.i("MESSAGE_SENT: payloadSize=${payload.size} bytes successfully sent to endpoint=$endpointId")
            }
            .addOnFailureListener { e ->
                MeshLogger.e("MESSAGE_SEND_FAILED: Failed sending payload to endpoint=$endpointId, ${formatError(e)}", e)
                if (connectedEndpoints.isEmpty()) {
                    updateStatusAfterFailure()
                }
            }
        MeshTransportResult.Accepted
    }.getOrElse { error ->
        MeshLogger.e("MESSAGE_SEND_FAILED: exception=${error.message}", error)
        MeshTransportResult.Rejected(error.message ?: "Unable to send payload")
    }

    override fun stop() {
        MeshLogger.i("MESH_STOPPED: Stopping advertising, discovery, and disconnecting all endpoints")
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        isAdvertising = false
        isDiscovering = false
        connectedEndpoints.clear()
        endpointNames.clear()
        _peers.value = emptyList()
        _status.value = MeshTransportStatus.STOPPED
    }

    private fun publishPeers() {
        _peers.value = connectedEndpoints.map { id ->
            MeshPeer(id, endpointNames[id] ?: id)
        }
    }

    private fun updateStatus() {
        _status.value = when {
            connectedEndpoints.isNotEmpty() -> MeshTransportStatus.CONNECTED
            isDiscovering -> MeshTransportStatus.DISCOVERING
            isAdvertising -> MeshTransportStatus.ADVERTISING
            else -> MeshTransportStatus.STOPPED
        }
    }

    private fun updateStatusAfterFailure() {
        _status.value = when {
            connectedEndpoints.isNotEmpty() -> MeshTransportStatus.CONNECTED
            isDiscovering -> MeshTransportStatus.DISCOVERING
            isAdvertising -> MeshTransportStatus.ADVERTISING
            else -> MeshTransportStatus.ERROR
        }
    }

    private fun handleAdvertisingFailure(e: Throwable) {
        if (e is ApiException) {
            when (e.statusCode) {
                ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING -> {
                    isAdvertising = true
                    MeshLogger.i("ADVERTISING_STARTED (already active): serviceId=$serviceId")
                    updateStatus()
                    return
                }
                8037, // STATUS_LOCATION_DISABLED
                ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR -> {
                    isAdvertising = false
                    MeshLogger.w("ADVERTISING_FAILED: ${formatError(e)}")
                    _status.value = MeshTransportStatus.UNAVAILABLE
                    return
                }
                8032, 8034, 8036, 8038, 8039, 8040, 8041 -> {
                    isAdvertising = false
                    MeshLogger.w("ADVERTISING_FAILED: ${formatError(e)}")
                    _status.value = MeshTransportStatus.PERMISSION_REQUIRED
                    return
                }
            }
        }
        isAdvertising = false
        MeshLogger.e("ADVERTISING_FAILED: ${formatError(e)}", e)
        updateStatusAfterFailure()
    }

    private fun handleDiscoveryFailure(e: Throwable) {
        if (e is ApiException) {
            when (e.statusCode) {
                ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING -> {
                    isDiscovering = true
                    MeshLogger.i("DISCOVERY_STARTED (already active): serviceId=$serviceId")
                    updateStatus()
                    return
                }
                8037, // STATUS_LOCATION_DISABLED
                ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR -> {
                    isDiscovering = false
                    MeshLogger.w("DISCOVERY_FAILED: ${formatError(e)}")
                    _status.value = MeshTransportStatus.UNAVAILABLE
                    return
                }
                8032, 8034, 8036, 8038, 8039, 8040, 8041 -> {
                    isDiscovering = false
                    MeshLogger.w("DISCOVERY_FAILED: ${formatError(e)}")
                    _status.value = MeshTransportStatus.PERMISSION_REQUIRED
                    return
                }
            }
        }
        isDiscovering = false
        MeshLogger.e("DISCOVERY_FAILED: ${formatError(e)}", e)
        updateStatusAfterFailure()
    }

    private fun formatError(e: Throwable): String {
        if (e is ApiException) {
            val code = e.statusCode
            val codeName = runCatching { ConnectionsStatusCodes.getStatusCodeString(code) }.getOrDefault("UNKNOWN_CODE")
            val explanation = when (code) {
                ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING -> "Already advertising on this service ID"
                ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING -> "Already discovering on this service ID"
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> "Already connected to endpoint"
                8037 -> "Location Services is disabled in Android settings"
                ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR -> "Bluetooth is disabled or encountered a radio error"
                ConnectionsStatusCodes.STATUS_OUT_OF_ORDER_API_CALL -> "API call executed out of order"
                8032 -> "Missing ACCESS_WIFI_STATE permission"
                8034 -> "Missing CHANGE_WIFI_STATE permission"
                8036 -> "Missing ACCESS_FINE_LOCATION permission"
                8038 -> "Missing BLUETOOTH_SCAN permission"
                8039 -> "Missing BLUETOOTH_ADVERTISE permission"
                8040 -> "Missing BLUETOOTH_CONNECT permission"
                8041 -> "Missing NEARBY_WIFI_DEVICES permission"
                else -> e.message ?: "Error $code"
            }
            return "ApiException(statusCode=$code, codeName=$codeName, detail=$explanation)"
        }
        return "${e.javaClass.simpleName}: ${e.message}"
    }
}

