package com.example.fyp.core.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoSplicer {
    private const val TAG = "VideoSplicer"

    suspend fun appendVideos(videoFiles: List<File>, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        if (videoFiles.isEmpty()) return@withContext false

        var muxer: MediaMuxer? = null
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoExtractor = MediaExtractor()
            val firstVideoPath = videoFiles[0].absolutePath
            videoExtractor.setDataSource(firstVideoPath)

            // Find Video and Audio Tracks
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    videoFormat = format
                } else if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }

            // Add tracks to muxer
            val muxerVideoIndex = videoFormat?.let { muxer.addTrack(it) } ?: -1
            val muxerAudioIndex = audioFormat?.let { muxer.addTrack(it) } ?: -1

            // handle orientation
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(firstVideoPath)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val rotationDegrees = rotationStr?.toIntOrNull() ?: 0
                muxer.setOrientationHint(rotationDegrees)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract rotation metadata: ${e.message}")
            } finally {
                retriever.release()
            }
            // ---------------------------------------------

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var videoOffset = 0L
            var audioOffset = 0L

            videoFiles.forEach { file ->
                val extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)

                // Process Video Track
                if (videoTrackIndex != -1) {
                    extractor.selectTrack(videoTrackIndex)
                    var maxPresentationTime = 0L // Track true maximum time

                    while (true) {
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) break

                        bufferInfo.presentationTimeUs = extractor.sampleTime + videoOffset

                        // Always store the highest timestamp to handle B-Frames
                        if (bufferInfo.presentationTimeUs > maxPresentationTime) {
                            maxPresentationTime = bufferInfo.presentationTimeUs
                        }

                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerVideoIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                    // Add a 33ms (1 frame) buffer to the highest timestamp
                    videoOffset = maxPresentationTime + 33000L
                    extractor.unselectTrack(videoTrackIndex)
                }

                // Process Audio Track
                if (audioTrackIndex != -1) {
                    extractor.selectTrack(audioTrackIndex)
                    var maxPresentationTime = 0L // Track true maximum time

                    while (true) {
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) break

                        bufferInfo.presentationTimeUs = extractor.sampleTime + audioOffset

                        if (bufferInfo.presentationTimeUs > maxPresentationTime) {
                            maxPresentationTime = bufferInfo.presentationTimeUs
                        }
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerAudioIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                    // 22ms buffer to the highest audio timestamp
                    audioOffset = maxPresentationTime + 22000L
                }
                extractor.release()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Splicing failed: ${e.message}")
            false
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) { /* Silent */ }
        }
    }
}