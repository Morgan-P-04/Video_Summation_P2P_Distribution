package com.example.fyp.core.network

import android.content.Context
import android.util.Log
import com.example.fyp.core.database.AppDatabase
import com.example.fyp.core.database.entities.SubscribedVideoEntity
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class P2pNetworkManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    // cluster strategy for M-to-N mesh networks (Epidemic Routing)
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.example.fyp.OPTIMISED_PUB_SUB"

    private var localUsername: String = "Unknown_Node"
    private val connectedEndpoints = mutableSetOf<String>()

    // DB and coroutine setup
    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    //  hold incoming files until they finish downloading
    private val incomingPayloads = mutableMapOf<Long, Payload>()

    // receive files and save to DB
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.FILE) {
                // the file has started transferring - store in map to track progress.
                incomingPayloads[payload.id] = payload
                Log.d("P2P", "Incoming video started from $endpointId")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    // calculate transfer percentage to watch in Logcat
                    if (update.totalBytes > 0) {
                        val progress = (update.bytesTransferred.toFloat() / update.totalBytes.toFloat() * 100).toInt()
                        Log.d("P2P", "Transferring with $endpointId: $progress% (${update.bytesTransferred} / ${update.totalBytes} bytes)")
                    }
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d("P2P", "Transfer 100% COMPLETE to/from $endpointId")
                    val payload = incomingPayloads.remove(update.payloadId)

                    if (payload != null && payload.type == Payload.Type.FILE) {
                        val tempFile = payload.asFile()?.asJavaFile()

                        if (tempFile != null) {
                            val finalFile = File(context.filesDir, "received_${System.currentTimeMillis()}.mp4")
                            tempFile.renameTo(finalFile)

                            // save to Room DB
                            scope.launch {
                                val receivedVideo = SubscribedVideoEntity(
                                    deliveryId = UUID.randomUUID().toString(),
                                    videoId = "video_${System.currentTimeMillis()}", // TODO replace placeholder ID
                                    subscriberId = localUsername,
                                    topicId = 1,    // TODO: fix metadata syncing
                                    sourcePeerId = endpointId,
                                    receivedAt = System.currentTimeMillis(),
                                    TTL = 24, // 24 hour purge
                                    deliveryState = "DELIVERED"
                                )
                                db.subscribedVideoDao().insertSubscribedVideo(receivedVideo)
                                Log.d("P2P", "Video saved to DB and storage!")
                            }
                        }
                    }
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    Log.e("P2P", "Payload transfer FAILED with $endpointId")
                    incomingPayloads.remove(update.payloadId)
                }
                PayloadTransferUpdate.Status.CANCELED -> {
                    Log.w("P2P", "Payload transfer CANCELED with $endpointId")
                    incomingPayloads.remove(update.payloadId)
                }
            }
        }
    }

    // connection lifecycle (auto accept logic)
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d("P2P", "Found node ${connectionInfo.endpointName}. auto-accepting")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                Log.d("P2P", "Successfully connected to $endpointId. sending videos")
                connectedEndpoints.add(endpointId)

                // send videos to new node
                scope.launch {
                    val myVideos = db.publishedVideoDao().getMyPublishedVideos().first()

                    myVideos.forEach { video ->
                        val file = File(video.localPath)
                        if (file.exists()) {
                            // Turn the file into a Payload and send it across the room
                            val filePayload = Payload.fromFile(file)
                            connectionsClient.sendPayload(endpointId, filePayload)
                            Log.d("P2P", "Sending ${file.name} to $endpointId")
                        }
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
        }
    }

    // endpoint discovery (looking for other phones/nodes)
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d("P2P", "Discovered endpoint $endpointId. Requesting connection")
            // as soon as another node is seen, actively try to connect to it
            connectionsClient.requestConnection(localUsername, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e ->
                    Log.e("P2P", "Failed to request connection: ${e.message}")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d("P2P", "Endpoint lost: $endpointId")
        }
    }

    // public controls

    fun startP2p(username: String) {
        localUsername = username

        // start advertising
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            localUsername, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener { Log.d("P2P", "Advertising started") }
            .addOnFailureListener { Log.e("P2P", "Advertising failed: ${it.message}") }

        // start discovering
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
        ).addOnSuccessListener { Log.d("P2P", "Discovery started") }
            .addOnFailureListener { Log.e("P2P", "Discovery failed: ${it.message}") }
    }

    fun stopP2p() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        Log.d("P2P", "P2P completely stopped.")
    }
}