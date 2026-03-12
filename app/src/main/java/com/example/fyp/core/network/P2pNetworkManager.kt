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

data class VideoMetadata(val topicId: Int, val publisherId: String, val title: String, val videoId: String)
class P2pNetworkManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    // cluster strategy for M-to-N mesh networks (Epidemic Routing)
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.example.fyp.OPTIMISED_PUB_SUB"
    // persistent publisherID
    private val prefs = context.getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)
    private val localNodeId: String = prefs.getString("node_id", null) ?: run {
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("node_id", newId).apply()
        newId
    }

    private var localUsername: String = "Unknown_Node"
    private val connectedEndpoints = mutableSetOf<String>()

    // DB and coroutine setup
    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    //  hold incoming files until they finish downloading
    private val incomingPayloads = mutableMapOf<Long, Payload>()
    // temporarily holds Topic IDs, original publisherID, and title until the matching file finishes downloading
    private val pendingVideoMetadata = mutableMapOf<Long, VideoMetadata>()

    // receive files and save to DB
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    // catch metadata packet
                    val metadataStr = String(payload.asBytes()!!, Charsets.UTF_8)
                    val parts = metadataStr.split(",", limit = 5)

                    if (parts.size == 5) {
                        val filePayloadId = parts[0].toLongOrNull()
                        val topicId = parts[1].toIntOrNull()
                        val publisherId = parts[2]
                        val videoTitle = parts[3]
                        val networkVideoId = parts[4] // catch universal ID

                        if (filePayloadId != null && topicId != null) {
                            scope.launch {
                                // database summary vector check
                                val havePublished = db.publishedVideoDao().doesVideoExist(networkVideoId)
                                val haveSubscribed = db.subscribedVideoDao().doesVideoExist(networkVideoId)

                                if (publisherId == localNodeId || havePublished || haveSubscribed) {
                                    Log.w("P2P", "Echo Blocked! We already have video $networkVideoId. Canceling download.")
                                    connectionsClient.cancelPayload(filePayloadId)
                                } else {
                                    // It's a new video! Save the metadata object
                                    pendingVideoMetadata[filePayloadId] = VideoMetadata(topicId, publisherId, videoTitle, networkVideoId)
                                }
                            }
                        }
                    }
                }
                Payload.Type.FILE -> {
                    incomingPayloads[payload.id] = payload
                    Log.d("P2P", "Incoming video started from $endpointId")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    if (update.totalBytes > 0) {
                        val progress = (update.bytesTransferred.toFloat() / update.totalBytes.toFloat() * 100).toInt()
                        Log.d("P2P", "Transferring with $endpointId: $progress%")
                    }
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    val payload = incomingPayloads.remove(update.payloadId)
                    val metadata = pendingVideoMetadata.remove(update.payloadId)

                    // If metadata got lost, generate fallbacks
                    val syncedTopicId = metadata?.topicId ?: 1
                    val originalPublisherId = metadata?.publisherId ?: "Unknown_Publisher"
                    val syncedTitle = metadata?.title ?: "Untitled Highlight"

                    // universal network ID
                    val uniqueId = metadata?.videoId ?: System.currentTimeMillis().toString()

                    val payloadFile = payload?.asFile()
                    if (payloadFile != null) {
                        scope.launch {
                            try {
                                val uniqueId = System.currentTimeMillis().toString()
                                val finalFile = File(context.filesDir, "received_$uniqueId.mp4")

                                val uri = payloadFile.asUri()
                                if (uri != null) {
                                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                        finalFile.outputStream().use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                } else {
                                    payloadFile.asJavaFile()?.copyTo(finalFile, overwrite = true)
                                }
                                payloadFile.asJavaFile()?.delete()

                                val receivedVideo = SubscribedVideoEntity(
                                    deliveryId = java.util.UUID.randomUUID().toString(),
                                    title = syncedTitle,
                                    videoId = uniqueId,
                                    subscriberId = originalPublisherId,
                                    topicId = syncedTopicId,
                                    sourcePeerId = endpointId,
                                    receivedAt = System.currentTimeMillis(),
                                    TTL = 24, // 24 hour purge
                                    deliveryState = "DELIVERED"
                                )
                                db.subscribedVideoDao().insertSubscribedVideo(receivedVideo)
                                Log.d("P2P", "Video saved! Topic ID: $syncedTopicId, Publisher: $originalPublisherId")
                            } catch (e: Exception) {
                                Log.e("P2P", "Failed to copy and save video: ${e.message}")
                            }
                        }
                    }
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    incomingPayloads.remove(update.payloadId)
                    pendingVideoMetadata.remove(update.payloadId)
                }
                PayloadTransferUpdate.Status.CANCELED -> {
                    incomingPayloads.remove(update.payloadId)
                    pendingVideoMetadata.remove(update.payloadId)
                }
            }
        }
    }

    // connection lifecycle (auto accept logic)
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {

            // prevent the phone from connecting to itself
            if (connectionInfo.endpointName == localUsername) {
                Log.w("P2P", "Discovered myself (${connectionInfo.endpointName}). Rejecting ghost connection.")
                connectionsClient.rejectConnection(endpointId)
            } else {
                Log.d("P2P", "Found node ${connectionInfo.endpointName}. auto-accepting")
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            }
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
                            // prepare file payload
                            val filePayload = Payload.fromFile(file)
                            // Format: payloadId,topicId,publisherId
                            val metadataString = "${filePayload.id},${video.topicId},$localNodeId,${video.title},${video.videoId}"
                            val metadataPayload = Payload.fromBytes(metadataString.toByteArray(Charsets.UTF_8))

                            connectionsClient.sendPayload(endpointId, metadataPayload)
                            connectionsClient.sendPayload(endpointId, filePayload)
                            Log.d("P2P", "Seeding published video to $endpointId")
                        }
                    }

                    // forward received videos (pub-sub mesh)
                    val foreignVideos = db.subscribedVideoDao().getMySubscribedVideos().first()
                    foreignVideos.forEach { video ->
                        val file = File(context.filesDir, "received_${video.videoId}.mp4")
                        if (file.exists()) {
                            val filePayload = Payload.fromFile(file)
                            val metadataString = "${filePayload.id},${video.topicId},${video.subscriberId},${video.title},${video.videoId}"
                            val metadataPayload = Payload.fromBytes(metadataString.toByteArray(Charsets.UTF_8))

                            connectionsClient.sendPayload(endpointId, metadataPayload)
                            connectionsClient.sendPayload(endpointId, filePayload)
                            Log.d("P2P", "Forwarding foreign video to $endpointId")
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
        // force a unique name if the memory is empty
        localUsername = if (username == "Unknown_Node" || username.isBlank()) {
            "Node_${(1000..9999).random()}"
        } else {
            username
        }

        // start advertising
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            localUsername, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener { Log.d("P2P", "Advertising started as $localUsername") }
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