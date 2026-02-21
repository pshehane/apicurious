package net.shehane.androidapiplayground.ui.screens

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileWriter
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.util.Range
import android.view.Surface
import android.view.TextureView
import java.util.concurrent.Executor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import net.shehane.androidapiplayground.ui.components.FeatureScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File
import java.nio.ByteBuffer

@Composable
fun BenchmarksScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // We hoist textureView so we can use it in both camera AND playback
    var textureView: TextureView? by remember { mutableStateOf(null) }
    var outputText by remember { mutableStateOf("Ready to Benchmark AV1, HEVC, APV") }
    
    // Camera Permissions
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasPermission = isGranted }
    )

    // Camera state
    var showCamera by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCpuLoadRunning by remember { mutableStateOf(false) }
    var showPlaybackOverlay by remember { mutableStateOf(false) }
    
    // Memory to hold YUV frames
    // 60 frames of 1080x1920 YUV420 data is ~186 MB (safely under 256MB limit)
    val capturedFrames = remember { mutableListOf<ByteArray>() }
    
    // File paths
    val av1File = File(context.filesDir, "test_av1.mp4")
    val hevcFile = File(context.filesDir, "test_hevc.mp4")
    val apvFile = File(context.filesDir, "test_apv.mp4")
    val audioFile = File(context.filesDir, "test_audio.m4a")

    fun appendLog(msg: String) {
        outputText += "\n$msg"
    }

    suspend fun generateTestPattern() {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    appendLog("Starting generation...")
                }

                fun encodeVideo(mimeType: String, outputFile: File) {
                    if (outputFile.exists()) outputFile.delete()
                    
                    val width = 1920
                    val height = 1080
                    val frameRate = 30
                    val totalFrames = 150
                    val bitRate = if (mimeType == "video/apv") 100_000_000 else 10_000_000

                    val format = MediaFormat.createVideoFormat(mimeType, width, height)
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                    val codec = MediaCodec.createEncoderByType(mimeType)
                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    
                    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    var trackIndex = -1
                    var muxerStarted = false

                    codec.start()
                    
                    val overlay = TextOverlayBox(width, height)
                    val displayMime = when (mimeType) {
                        "video/av01" -> "AV1"
                        "video/hevc" -> "HEVC"
                        "video/apv" -> "APV"
                        else -> mimeType
                    }

                    val bufferInfo = MediaCodec.BufferInfo()
                    var inputFrameIndex = 0
                    var outputDone = false
                    val startTime = System.currentTimeMillis()

                    while (!outputDone) {
                        // Feed Input
                        if (inputFrameIndex < totalFrames) {
                            val inputBufferId = codec.dequeueInputBuffer(10000)
                            if (inputBufferId >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputBufferId)
                                if (inputBuffer != null) {
                                    val inputImage = codec.getInputImage(inputBufferId)
                                    val capacity = inputBuffer.capacity()
                                    if (inputImage != null) {
                                        val yPlane = inputImage.planes[0].buffer
                                        val yStride = inputImage.planes[0].rowStride
                                        val uPlane = inputImage.planes[1].buffer
                                        val uStride = inputImage.planes[1].rowStride
                                        val uPixelStride = inputImage.planes[1].pixelStride
                                        val vPlane = inputImage.planes[2].buffer
                                        val vStride = inputImage.planes[2].rowStride
                                        val vPixelStride = inputImage.planes[2].pixelStride

                                        val t = (inputFrameIndex % 30) / 30.0
                                        val yValue = (t * 255).toInt().toByte()
                                        
                                        val rowBytes = ByteArray(width) { yValue }
                                        for (y in 0 until height) {
                                            yPlane.position(y * yStride)
                                            yPlane.put(rowBytes)
                                        }
                                        
                                        for (y in 0 until height / 2) {
                                            for (x in 0 until width / 2) {
                                                uPlane.put(y * uStride + x * uPixelStride, 128.toByte())
                                                vPlane.put(y * vStride + x * vPixelStride, 128.toByte())
                                            }
                                        }
                                        
                                        overlay.overlayText("$displayMime\nFrame: $inputFrameIndex", inputImage)
                                    } else {
                                        val ySize = width * height
                                        val t = (inputFrameIndex % 30) / 30.0
                                        val yValue = (t * 255).toInt().toByte()
                                        for (i in 0 until ySize) {
                                            inputBuffer.put(i, yValue)
                                        }
                                        for (i in ySize until capacity) {
                                            inputBuffer.put(i, 128.toByte())
                                        }
                                    }

                                    // 1 frame = 1,000,000 us / 30 fps = 33333.33 us, but we need Long integers.
                                    val ptr = (inputFrameIndex.toLong() * 1_000_000L) / frameRate
                                    codec.queueInputBuffer(inputBufferId, 0, capacity, ptr, 0)
                                    inputFrameIndex++
                                }
                            }
                        } else {
                            // Signal End of Stream
                            val inputBufferId = codec.dequeueInputBuffer(10000)
                            if (inputBufferId >= 0) {
                                codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        }

                        // Read Output
                        val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        if (outputBufferId >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferId)
                            if (outputBuffer != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }

                                if (bufferInfo.size != 0) {
                                    if (!muxerStarted) {
                                        throw RuntimeException("muxer hasn't started")
                                    }
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                }

                                codec.releaseOutputBuffer(outputBufferId, false)
                                
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                }
                            }
                        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (muxerStarted) {
                                throw RuntimeException("format changed twice")
                            }
                            val newFormat = codec.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }

                    codec.stop()
                    codec.release()
                    muxer.stop()
                    muxer.release()
                    
                    val timeTaken = System.currentTimeMillis() - startTime
                    throw RuntimeException("Success: ${timeTaken}ms")
                }

                fun encodeAudio(outputFile: File) {
                    if (outputFile.exists()) outputFile.delete()
                    
                    val sampleRate = 44100
                    val channelCount = 2
                    val bitRate = 128000 // 128kbps
                    val durationSecs = 5
                    val totalSamples = sampleRate * durationSecs

                    val mimeType = MediaFormat.MIMETYPE_AUDIO_AAC
                    val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)
                    format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

                    val codec = MediaCodec.createEncoderByType(mimeType)
                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    
                    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    var trackIndex = -1
                    var muxerStarted = false

                    codec.start()
                    
                    val bufferInfo = MediaCodec.BufferInfo()
                    var inputSamplesProcessed = 0
                    var outputDone = false
                    val startTime = System.currentTimeMillis()
                    
                    // Buffer to hold generated PCM data (16-bit)
                    // We'll generate chunks of data
                    val pcmChunkSize = 4096 // samples per channel
                    
                    while (!outputDone) {
                         // Feed Input
                        if (inputSamplesProcessed < totalSamples) {
                            val inputBufferId = codec.dequeueInputBuffer(10000)
                            if (inputBufferId >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputBufferId)
                                if (inputBuffer != null) {
                                    val samplesToWrite = minOf(pcmChunkSize, totalSamples - inputSamplesProcessed)
                                    val bytesToWrite = samplesToWrite * channelCount * 2 // 16-bit
                                    
                                    // Generate sine wave
                                    for (i in 0 until samplesToWrite) {
                                        val t = (inputSamplesProcessed + i).toDouble() / sampleRate
                                        val sampleVal = (Math.sin(2.0 * Math.PI * 440.0 * t) * 32767).toInt().toShort()
                                        // Interleave (same for both channels)
                                        inputBuffer.putShort(sampleVal) // Left
                                        inputBuffer.putShort(sampleVal) // Right
                                    }
                                    
                                    val ptr = inputSamplesProcessed * 1000000L / sampleRate
                                    codec.queueInputBuffer(inputBufferId, 0, bytesToWrite, ptr, 0)
                                    inputSamplesProcessed += samplesToWrite
                                }
                            }
                        } else {
                             val inputBufferId = codec.dequeueInputBuffer(10000)
                            if (inputBufferId >= 0) {
                                codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                            // Don't wait here, unlike video loop, logic flows same
                        }

                        // Read Output
                        val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        if (outputBufferId >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferId)
                            if (outputBuffer != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }

                                if (bufferInfo.size != 0) {
                                    if (!muxerStarted) {
                                        throw RuntimeException("muxer hasn't started")
                                    }
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                }

                                codec.releaseOutputBuffer(outputBufferId, false)
                                
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                }
                            }
                        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (muxerStarted) {
                                throw RuntimeException("format changed twice")
                            }
                            val newFormat = codec.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }

                    codec.stop()
                    codec.release()
                    muxer.stop()
                    muxer.release()
                    val timeTaken = System.currentTimeMillis() - startTime
                    throw RuntimeException("Success: ${timeTaken}ms")
                }

                // AV1
                withContext(Dispatchers.Main) { appendLog("Encoding AV1...") }
                try {
                    encodeVideo(MediaFormat.MIMETYPE_VIDEO_AV1, av1File)
                    withContext(Dispatchers.Main) { appendLog("AV1 Created.") }
                } catch (e: Exception) {
                     if (e.message?.startsWith("Success") == true) {
                         withContext(Dispatchers.Main) { appendLog("AV1: ${e.message}") }
                     } else {
                         withContext(Dispatchers.Main) { appendLog("AV1 Setup Failed (Likely no encoder): ${e.message}") }
                     }
                }

                // HEVC
                withContext(Dispatchers.Main) { appendLog("Encoding HEVC...") }
                try {
                    encodeVideo(MediaFormat.MIMETYPE_VIDEO_HEVC, hevcFile)
                     withContext(Dispatchers.Main) { appendLog("HEVC Created.") }
                } catch (e: Exception) {
                    if (e.message?.startsWith("Success") == true) {
                         withContext(Dispatchers.Main) { appendLog("HEVC: ${e.message}") }
                     } else {
                         withContext(Dispatchers.Main) { appendLog("HEVC Setup Failed (Likely no encoder): ${e.message}") }
                     }
                }

                // APV
                withContext(Dispatchers.Main) { appendLog("Encoding APV...") }
                try {
                    encodeVideo("video/apv", apvFile) // Use string as constant might not be available
                     withContext(Dispatchers.Main) { appendLog("APV Created.") }
                } catch (e: Exception) {
                    if (e.message?.startsWith("Success") == true) {
                         withContext(Dispatchers.Main) { appendLog("APV: ${e.message}") }
                     } else {
                         // Common failure point for non-supported devices
                         withContext(Dispatchers.Main) { appendLog("APV Setup Failed (Likely no encoder): ${e.message}") }
                     }
                }

                // Audio (AAC) - REMOVED

                withContext(Dispatchers.Main) {
                    appendLog("Generation Complete.")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("Error generating: ${e.message}")
                }
            }
        }
    }

    suspend fun copyToMediaStore(context: Context, file: File, mimeType: String, title: String) {
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) return@withContext
                val resolver = context.contentResolver
                
                // 1. Delete existing identical benchmark file if it exists to enforce overwrites
                val targetName = "${title}.mp4"
                val relativePath = android.os.Environment.DIRECTORY_MOVIES + "/ApiPlaygroundBenchmarks/"
                
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.Video.Media.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf(targetName, relativePath)
                val cursor = resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    selection,
                    selectionArgs,
                    null
                )
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val id = it.getLong(idColumn)
                        val existingUri = android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        resolver.delete(existingUri, null, null)
                    }
                }

                // 2. Insert new file with the static name
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        java.io.FileInputStream(file).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    withContext(Dispatchers.Main) { appendLog("Exported $title to MediaStore.") }
                } else {
                    withContext(Dispatchers.Main) { appendLog("Failed to create MediaStore entry for $title.") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLog("MediaStore Copy Error ($title): ${e.message}") }
            }
        }
    }

    suspend fun encodeCapturedFrames() {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    appendLog("Starting encoding from camera...")
                }

                fun encodeVideoFromFrames(mimeType: String, outputFile: File) {
                    if (outputFile.exists()) outputFile.delete()
                    
                    val width = 1080 // portrait
                    val height = 1920
                    val frameRate = 30
                    val totalFrames = capturedFrames.size * 10 // Loop 10 times for 20s video
                    val bitRate = if (mimeType == "video/apv") 100_000_000 else 10_000_000

                    val format = MediaFormat.createVideoFormat(mimeType, width, height)
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                    val codec = MediaCodec.createEncoderByType(mimeType)
                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    
                    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    var trackIndex = -1
                    var muxerStarted = false

                    codec.start()
                    
                    val overlay = TextOverlayBox(width, height)
                    val displayMime = when (mimeType) {
                        "video/av01" -> "AV1"
                        "video/hevc" -> "HEVC"
                        "video/apv" -> "APV"
                        else -> mimeType
                    }

                    val bufferInfo = MediaCodec.BufferInfo()
                    var inputFrameIndex = 0
                    var outputDone = false
                    val startTime = System.currentTimeMillis()

                    while (!outputDone) {
                        // Feed Input
                        if (inputFrameIndex < totalFrames) {
                            val inputBufferId = codec.dequeueInputBuffer(10000)
                            if (inputBufferId >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputBufferId)
                                if (inputBuffer != null) {
                                    val sourceIndex = inputFrameIndex % capturedFrames.size
                                    val frameData = capturedFrames[sourceIndex]
                                    
                                    val inputImage = codec.getInputImage(inputBufferId)
                                    val capacity = inputBuffer.capacity()
                                    if (inputImage != null) {
                                        val yPlane = inputImage.planes[0].buffer
                                        val yStride = inputImage.planes[0].rowStride
                                        val uPlane = inputImage.planes[1].buffer
                                        val uStride = inputImage.planes[1].rowStride
                                        val uPixelStride = inputImage.planes[1].pixelStride
                                        val vPlane = inputImage.planes[2].buffer
                                        val vStride = inputImage.planes[2].rowStride
                                        val vPixelStride = inputImage.planes[2].pixelStride

                                        var srcOffset = 0
                                        // Copy Y
                                        for (y in 0 until height) {
                                            yPlane.position(y * yStride)
                                            yPlane.put(frameData, srcOffset, width)
                                            srcOffset += width
                                        }

                                        // Copy UV (our frameData is NV12 tightly packed: U V U V)
                                        val uvStart = srcOffset
                                        for (y in 0 until height / 2) {
                                            for (x in 0 until width / 2) {
                                                val uVal = frameData[uvStart + y * width + x * 2]
                                                val vVal = frameData[uvStart + y * width + x * 2 + 1]
                                                uPlane.put(y * uStride + x * uPixelStride, uVal)
                                                vPlane.put(y * vStride + x * vPixelStride, vVal)
                                            }
                                        }

                                        overlay.overlayText("$displayMime\nFrame: $inputFrameIndex", inputImage)
                                    } else {
                                        inputBuffer.put(frameData)
                                    }

                                    val ptr = (inputFrameIndex.toLong() * 1_000_000L) / frameRate
                                    codec.queueInputBuffer(inputBufferId, 0, capacity, ptr, 0)
                                    inputFrameIndex++
                                }
                            }
                        } else {
                            // Signal End of Stream
                            val inputBufferId = codec.dequeueInputBuffer(10000)
                            if (inputBufferId >= 0) {
                                codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        }

                        // Read Output
                        val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        if (outputBufferId >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferId)
                            if (outputBuffer != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }

                                if (bufferInfo.size != 0) {
                                    if (!muxerStarted) {
                                        throw RuntimeException("muxer hasn't started")
                                    }
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                }

                                codec.releaseOutputBuffer(outputBufferId, false)
                                
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                }
                            }
                        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (muxerStarted) {
                                throw RuntimeException("format changed twice")
                            }
                            val newFormat = codec.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }

                    codec.stop()
                    codec.release()
                    muxer.stop()
                    muxer.release()
                    
                    val timeTaken = System.currentTimeMillis() - startTime
                    throw RuntimeException("Success: ${timeTaken}ms")
                }

                // AV1
                withContext(Dispatchers.Main) { appendLog("Encoding AV1 from camera...") }
                try {
                    encodeVideoFromFrames("video/av01", av1File)
                    withContext(Dispatchers.Main) { appendLog("AV1 Created.") }
                    copyToMediaStore(context, av1File, "video/mp4", "Benchmark_AV1")
                } catch (e: Exception) {
                     if (e.message?.startsWith("Success") == true) {
                         withContext(Dispatchers.Main) { appendLog("AV1: ${e.message}") }
                         copyToMediaStore(context, av1File, "video/mp4", "Benchmark_AV1")
                     } else {
                         withContext(Dispatchers.Main) { appendLog("AV1 Setup Failed (Likely no encoder): ${e.message}") }
                     }
                }

                // HEVC
                withContext(Dispatchers.Main) { appendLog("Encoding HEVC from camera...") }
                try {
                    encodeVideoFromFrames(MediaFormat.MIMETYPE_VIDEO_HEVC, hevcFile)
                     withContext(Dispatchers.Main) { appendLog("HEVC Created.") }
                     copyToMediaStore(context, hevcFile, "video/mp4", "Benchmark_HEVC")
                } catch (e: Exception) {
                    if (e.message?.startsWith("Success") == true) {
                         withContext(Dispatchers.Main) { appendLog("HEVC: ${e.message}") }
                         copyToMediaStore(context, hevcFile, "video/mp4", "Benchmark_HEVC")
                     } else {
                         withContext(Dispatchers.Main) { appendLog("HEVC Setup Failed (Likely no encoder): ${e.message}") }
                     }
                }

                // APV
                withContext(Dispatchers.Main) { appendLog("Encoding APV from camera...") }
                try {
                    encodeVideoFromFrames("video/apv", apvFile) // Use string as constant might not be available
                     withContext(Dispatchers.Main) { appendLog("APV Created.") }
                     copyToMediaStore(context, apvFile, "video/mp4", "Benchmark_APV")
                } catch (e: Exception) {
                    if (e.message?.startsWith("Success") == true) {
                         withContext(Dispatchers.Main) { appendLog("APV: ${e.message}") }
                         copyToMediaStore(context, apvFile, "video/mp4", "Benchmark_APV")
                     } else {
                         // Common failure point for non-supported devices
                         withContext(Dispatchers.Main) { appendLog("APV Setup Failed (Likely no encoder): ${e.message}") }
                     }
                }

                withContext(Dispatchers.Main) {
                    appendLog("Camera Generation Complete.")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("Error generating: ${e.message}")
                }
            }
        }
    }

    fun findDecoders() {
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val relevantTypes = listOf(
                MediaFormat.MIMETYPE_VIDEO_AV1, 
                MediaFormat.MIMETYPE_VIDEO_HEVC, 
                "video/apv", 
                MediaFormat.MIMETYPE_AUDIO_AAC
            )
            val decoders = codecList.codecInfos.filter { info -> 
                !info.isEncoder && relevantTypes.any { type -> info.supportedTypes.contains(type) } && !info.name.contains("secure", ignoreCase = true)
            }
            
            val sb = StringBuilder()
            sb.append("\nFound ${decoders.size} decoders:\n")
            decoders.forEach { info ->
                val types = info.supportedTypes.filter { relevantTypes.contains(it) }.joinToString(", ") { type ->
                    when (type) {
                        MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1"
                        MediaFormat.MIMETYPE_VIDEO_HEVC -> "HEVC"
                        "video/apv" -> "APV"
                        MediaFormat.MIMETYPE_AUDIO_AAC -> "AAC"
                        else -> type
                    }
                }
                val isSw = if (info.isSoftwareOnly) "SW" else "HW"
                sb.append("- ${info.name} ($types, $isSw)\n")
            }
            appendLog(sb.toString())
        } catch (e: Exception) {
            appendLog("Error finding decoders: ${e.message}")
        }
    }

    suspend fun runBenchmarks(renderToSurface: Boolean = false) {
        withContext(Dispatchers.IO) {
             if (!av1File.exists() && !hevcFile.exists() && !apvFile.exists()) {
                withContext(Dispatchers.Main) {
                    appendLog("Files not found. Please Generate Test Pattern or Camera first.")
                 }
                return@withContext
            }

            try {
                withContext(Dispatchers.Main) { 
                    outputText = "Running Benchmarks...\n" // Clear previous or append? User request said "showing the user what you are doing"
                    // Let's clear to keep it clean or just append separator.
                    appendLog("--- Starting Benchmark Run ---")
                }

                data class Result(
                    val name: String, 
                    val type: String, 
                    val timeMs: Long, 
                    val frameCount: Int, 
                    val fps: Float,
                    val minLatency: Long,
                    val maxLatency: Long,
                    val avgLatency: Double,
                    val minJitter: Long,
                    val maxJitter: Long,
                    val avgJitter: Double
                )

                val results = mutableListOf<Result>()

                // Helper function to benchmark a specific decoder
                suspend fun benchmarkDecoder(decoderName: String, file: File, mimeType: String, renderToSurface: Boolean = false): Result? {
                    return try {
                         withContext(Dispatchers.Main) { appendLog("Benchmarking $decoderName...") }
                        
                        val extractor = MediaExtractor()
                        extractor.setDataSource(file.absolutePath)
                        
                        var trackIndex = -1
                        var inputFormat: MediaFormat? = null
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            val mime = format.getString(MediaFormat.KEY_MIME)
                            if (mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true) {
                                trackIndex = i
                                inputFormat = format
                                break
                            }
                        }

                        if (trackIndex < 0 || inputFormat == null) {
                            extractor.release()
                            throw RuntimeException("No track found")
                        }

                        extractor.selectTrack(trackIndex)

                        val inputTimes = mutableMapOf<Long, Long>()
                        val latencies = mutableListOf<Long>()
                        val jitters = mutableListOf<Long>()
                        var lastOutputTime: Long? = null

                        val codec = MediaCodec.createByCodecName(decoderName)
                        
                        var surface: Surface? = null
                        if (renderToSurface && textureView != null) {
                            val st = textureView!!.surfaceTexture
                            if (st != null) {
                                surface = Surface(st)
                            }
                        }
                        
                        codec.configure(inputFormat, surface, null, 0)
                        codec.start()

                        val bufferInfo = MediaCodec.BufferInfo()
                        var inputDone = false
                        var outputDone = false
                        var frameCount = 0
                        val startTime = System.currentTimeMillis()

                        while (!outputDone) {
                            if (!inputDone) {
                                val inputBufferId = codec.dequeueInputBuffer(10000)
                                if (inputBufferId >= 0) {
                                    val inputBuffer = codec.getInputBuffer(inputBufferId)
                                    if (inputBuffer != null) {
                                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                        if (sampleSize < 0) {
                                            codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                            inputDone = true
                                        } else {
                                            val presentationTimeUs = extractor.sampleTime
                                            inputTimes[presentationTimeUs] = System.nanoTime()
                                            codec.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                                            extractor.advance()
                                        }
                                    }
                                }
                            }

                            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000)
                            val outputTime = System.nanoTime()
                            if (outputBufferId >= 0) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                }
                                
                                if (bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                    val inputTime = inputTimes[bufferInfo.presentationTimeUs]
                                    if (inputTime != null) {
                                        latencies.add(outputTime - inputTime)
                                    }
                                    if (lastOutputTime != null) {
                                        jitters.add(outputTime - lastOutputTime!!)
                                    }
                                    lastOutputTime = outputTime
                                    frameCount++
                                }
                                
                                codec.releaseOutputBuffer(outputBufferId, renderToSurface)
                            }
                        }
                        
                        val duration = System.currentTimeMillis() - startTime
                        val fps = if (duration > 0) (frameCount * 1000f) / duration else 0f
                        
                        codec.stop()
                        codec.release()
                        extractor.release()

                        val minLat = latencies.minOrNull() ?: 0L
                        val maxLat = latencies.maxOrNull() ?: 0L
                        val avgLat = if (latencies.isNotEmpty()) latencies.average() else 0.0

                        val minJit = jitters.minOrNull() ?: 0L
                        val maxJit = jitters.maxOrNull() ?: 0L
                        val avgJit = if (jitters.isNotEmpty()) jitters.average() else 0.0

                        Result(decoderName, mimeType, duration, frameCount, fps, minLat, maxLat, avgLat, minJit, maxJit, avgJit)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) { appendLog("  Failed: ${e.message}") }
                        null
                    }
                }

                val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
                
                // Find and Run AV1 Decoders
                if (av1File.exists()) {
                    val av1Decoders = codecList.codecInfos.filter { !it.isEncoder && it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_AV1) && !it.name.contains("secure", ignoreCase = true) }
                    if (av1Decoders.isEmpty()) {
                         withContext(Dispatchers.Main) { appendLog("No AV1 Decoders found.") }
                    }
                    av1Decoders.forEach { info ->
                        val res = benchmarkDecoder(info.name, av1File, "AV1", renderToSurface)
                        if (res != null) results.add(res)
                    }
                } else {
                    withContext(Dispatchers.Main) { appendLog("Skipping AV1 (File missing)") }
                }

                // Find and Run HEVC Decoders
                if (hevcFile.exists()) {
                    val hevcDecoders = codecList.codecInfos.filter { !it.isEncoder && it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_HEVC) && !it.name.contains("secure", ignoreCase = true) }
                     if (hevcDecoders.isEmpty()) {
                         withContext(Dispatchers.Main) { appendLog("No HEVC Decoders found.") }
                    }
                    hevcDecoders.forEach { info ->
                        val res = benchmarkDecoder(info.name, hevcFile, "HEVC", renderToSurface)
                        if (res != null) results.add(res)
                    }
                } else {
                    withContext(Dispatchers.Main) { appendLog("Skipping HEVC (File missing)") }
                }

                // APV 
                if (apvFile.exists()) {
                    val apvDecoders = codecList.codecInfos.filter { !it.isEncoder && it.supportedTypes.contains("video/apv") && !it.name.contains("secure", ignoreCase = true) }
                     if (apvDecoders.isEmpty()) {
                         withContext(Dispatchers.Main) { appendLog("No APV Decoders found.") }
                    }
                    apvDecoders.forEach { info ->
                        val res = benchmarkDecoder(info.name, apvFile, "APV", renderToSurface)
                        if (res != null) results.add(res)
                    }
                } else {
                    withContext(Dispatchers.Main) { appendLog("Skipping APV (File missing)") }
                }

                // Sort and Display
                // Sort and Display
                withContext(Dispatchers.Main) {
                    appendLog("\n--- Results (Fastest to Slowest) ---")
                    results.sortedByDescending { it.fps } // Fastest fps first
                        .forEach { res ->
                            appendLog("${res.name} (${res.type}): %.2f FPS (${res.timeMs}ms)".format(res.fps))
                            val nsToMs = 1_000_000.0
                            val minL = res.minLatency / nsToMs
                            val maxL = res.maxLatency / nsToMs
                            val avgL = res.avgLatency / nsToMs
                            val minJ = res.minJitter / nsToMs
                            val maxJ = res.maxJitter / nsToMs
                            val avgJ = res.avgJitter / nsToMs
                            appendLog("  Latency (ms): Min=%.2f, Max=%.2f, Avg=%.2f".format(minL, maxL, avgL))
                            appendLog("  Jitter (ms): Min=%.2f, Max=%.2f, Avg=%.2f\n".format(minJ, maxJ, avgJ))
                        }
                    appendLog("--- Done ---")
                }
                
                // Save JSON
                 withContext(Dispatchers.IO) {
                    try {
                        val file = File(context.getExternalFilesDir(null), "benchmark_results.json")
                        val jsonRoot = if (file.exists()) {
                            try {
                                val sb = StringBuilder()
                                BufferedReader(FileReader(file)).use { br ->
                                    var line = br.readLine()
                                    while (line != null) {
                                        sb.append(line)
                                        line = br.readLine()
                                    }
                                }
                                JSONObject(sb.toString())
                            } catch (e: Exception) {
                                JSONObject()
                            }
                        } else {
                            JSONObject()
                        }

                        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                        val deviceNode = if (jsonRoot.has(deviceModel)) jsonRoot.getJSONObject(deviceModel) else JSONObject()
                        
                        val timestamp = System.currentTimeMillis().toString()
                        val resultsArray = JSONArray()
                        results.forEach { res ->
                            val resObj = JSONObject()
                            resObj.put("name", res.name)
                            resObj.put("type", res.type)
                            resObj.put("timeMs", res.timeMs)
                            resObj.put("fps", res.fps.toDouble())
                            resObj.put("frameCount", res.frameCount)
                            resObj.put("minLatencyNs", res.minLatency)
                            resObj.put("maxLatencyNs", res.maxLatency)
                            resObj.put("avgLatencyNs", res.avgLatency)
                            resObj.put("minJitterNs", res.minJitter)
                            resObj.put("maxJitterNs", res.maxJitter)
                            resObj.put("avgJitterNs", res.avgJitter)
                            resultsArray.put(resObj)
                        }
                        
                        deviceNode.put(timestamp, resultsArray)
                        jsonRoot.put(deviceModel, deviceNode)
                        
                        FileWriter(file).use { it.write(jsonRoot.toString(4)) }
                        withContext(Dispatchers.Main) { 
                             appendLog("Results saved to ${file.absolutePath}") 
                        }
                    } catch (e: Exception) {
                         withContext(Dispatchers.Main) { 
                             appendLog("Error saving JSON: ${e.message}") 
                        }
                    }
                }

            } catch (e: Exception) {
                 withContext(Dispatchers.Main) {
                     appendLog("Error running benchmarks: ${e.message}")
                 }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FeatureScaffold(
            title = "AV1 & HEVC Benchmarks",
            onBackClick = onBackClick,
            controlsContent = {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasPermission) {
                    Button(onClick = { 
                        showCamera = true
                        countdown = 4 // 3, 2, 1, REC
                    }) {
                        Text("Generate from Camera")
                    }
                } else {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Camera")
                    }
                }
                Button(onClick = { scope.launch { generateTestPattern() } }) {
                    Text("Generate Pattern")
                }
                Button(onClick = { findDecoders() }) {
                    Text("Find All")
                }
                Button(onClick = { 
                    showPlaybackOverlay = true
                    scope.launch { 
                        runBenchmarks(renderToSurface = true) 
                        showPlaybackOverlay = false
                    } 
                }) {
                    Text("Play")
                }
                Button(onClick = { scope.launch { runBenchmarks(renderToSurface = false) } }) {
                    Text("Run")
                }
                Button(
                    onClick = { isCpuLoadRunning = !isCpuLoadRunning },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (isCpuLoadRunning) Color.Red else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isCpuLoadRunning) "Stop CPU Load" else "Start CPU Load")
                }
            }
        },
            outputContent = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    thumbnailBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "First Frame",
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .size(108.dp, 192.dp)
                        )
                    }
                    Text(
                        text = outputText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        )

    if (showCamera || showPlaybackOverlay) {
        val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
        var cameraDevice: CameraDevice? by remember { mutableStateOf(null) }
        var captureSession: CameraCaptureSession? by remember { mutableStateOf(null) }
        var imageReader: ImageReader? by remember { mutableStateOf(null) }
        val backgroundThread = remember { HandlerThread("CameraBackground").apply { start() } }
        val backgroundHandler = remember { Handler(backgroundThread.looper) }

        fun stopCamera() {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            // Don't quit the thread yet, it might still have events
            showCamera = false
        }

        fun startCameraCapture() {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull() ?: return

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val surfaceTexture = textureView!!.surfaceTexture
                        surfaceTexture?.setDefaultBufferSize(1080, 1920) // Portrait orientation
                        val previewSurface = Surface(surfaceTexture)

                        imageReader = ImageReader.newInstance(1080, 1920, ImageFormat.YUV_420_888, 5)
                        imageReader!!.setOnImageAvailableListener({ reader ->
                            if (isRecording && capturedFrames.size < 60) {
                                val image = reader.acquireNextImage()
                                if (image != null) {
                                    val data = processCameraImageToNV12(image, 1080, 1920, sensorOrientation)
                                    capturedFrames.add(data)
                                    
                                    if (capturedFrames.size == 1) {
                                        try {
                                            // Our helper outputs NV12 (U, V). YuvImage expects NV21 (V, U). We swap U/V on a clone for the thumbnail.
                                            val nv21Data = data.clone()
                                            val ySize = 1080 * 1920
                                            for (i in ySize until nv21Data.size step 2) {
                                                val temp = nv21Data[i]
                                                nv21Data[i] = nv21Data[i + 1]
                                                nv21Data[i + 1] = temp
                                            }
                                            
                                            val yuvImage = android.graphics.YuvImage(nv21Data, ImageFormat.NV21, 1080, 1920, null)
                                            val out = java.io.ByteArrayOutputStream()
                                            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, 1080, 1920), 50, out)
                                            val imageBytes = out.toByteArray()
                                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                                            if (bitmap != null) {
                                                val matrix = Matrix()
                                                matrix.postScale(0.2f, 0.2f)
                                                thumbnailBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    image.close()
                                }
                            } else {
                                reader.acquireLatestImage()?.close() // Just clear it
                            }
                        }, backgroundHandler)

                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        captureBuilder.addTarget(previewSurface)
                        captureBuilder.addTarget(imageReader!!.surface)
                        captureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))

                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(listOf(previewSurface, imageReader!!.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                session.setRepeatingRequest(captureBuilder.build(), null, backgroundHandler)
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                        }, backgroundHandler)

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, backgroundHandler)
        }

        LaunchedEffect(countdown) {
            if (countdown > 1) {
                delay(1000)
                countdown -= 1
            } else if (countdown == 1) {
                delay(1000)
                countdown = 0
                isRecording = true
                capturedFrames.clear()
            }
        }

        LaunchedEffect(isRecording) {
            if (isRecording) {
                while (capturedFrames.size < 60) {
                    delay(50) // Wait for frames to accumulate
                }
                isRecording = false
                stopCamera()
                // Now trigger encoding...
                scope.launch { encodeCapturedFrames() }
            }
        }

        LaunchedEffect(isCpuLoadRunning) {
            if (isCpuLoadRunning) {
                repeat(4) { threadIndex ->
                    launch(Dispatchers.Default) {
                        var sum = 0.0
                        for (i in 1..Int.MAX_VALUE) {
                            if (!isActive) break
                            sum += kotlin.math.sin(i.toDouble()) * kotlin.math.cos(i.toDouble())
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                textureView = this@apply
                                if (showCamera) {
                                    startCameraCapture()
                                }
                            }
                            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture) = true
                            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay text
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (countdown > 1) {
                    Text("${countdown - 1}", style = MaterialTheme.typography.displayLarge, color = Color.White)
                } else if (countdown == 1) {
                    Text("REC", style = MaterialTheme.typography.displayLarge, color = Color.Red)
                }
            }
        }
    }
    } // End of outer Box that wraps FeatureScaffold and Camera overlay
}

class TextOverlayBox(val frameWidth: Int, val frameHeight: Int) {
    val boxWidth = frameWidth / 3
    val boxHeight = frameHeight / 9
    val bitmap = Bitmap.createBitmap(boxWidth, boxHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = android.graphics.Color.RED
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    val bgPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#80000000") // Semi-transparent black
    }
    val pixels = IntArray(boxWidth * boxHeight)

    fun overlayText(text: String, inputImage: Image?) {
        if (inputImage == null) return
        
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        canvas.drawRect(0f, 0f, boxWidth.toFloat(), boxHeight.toFloat(), bgPaint)
        
        val lines = text.split("\n")
        var y = boxHeight / 2f - ((lines.size - 1) * 30f / 2f) + 15f
        for (line in lines) {
            canvas.drawText(line, boxWidth / 2f, y, paint)
            y += 40f
        }
        
        bitmap.getPixels(pixels, 0, boxWidth, 0, 0, boxWidth, boxHeight)
        
        val startX = (frameWidth - boxWidth) / 2
        val startY = (frameHeight - boxHeight) / 2
        
        val yPlane = inputImage.planes[0].buffer
        val yStride = inputImage.planes[0].rowStride
        val uPlane = inputImage.planes[1].buffer
        val uStride = inputImage.planes[1].rowStride
        val uPixelStride = inputImage.planes[1].pixelStride
        val vPlane = inputImage.planes[2].buffer
        val vStride = inputImage.planes[2].rowStride
        val vPixelStride = inputImage.planes[2].pixelStride

        for (by in 0 until boxHeight) {
            for (bx in 0 until boxWidth) {
                val pixel = pixels[by * boxWidth + bx]
                val a = android.graphics.Color.alpha(pixel)
                if (a == 0) continue

                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)

                // BT.601
                val yVal = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val uVal = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val vVal = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                val fy = startY + by
                val fx = startX + bx

                // Blend Y
                val yIndex = fy * yStride + fx
                val oldY = yPlane.get(yIndex).toInt() and 0xFF
                val newY = ((yVal * a + oldY * (255 - a)) / 255).toByte()
                yPlane.put(yIndex, newY)

                // Blend UV (every 2x2 block)
                if (fy % 2 == 0 && fx % 2 == 0) {
                    val uIndex = (fy / 2) * uStride + (fx / 2) * uPixelStride
                    val vIndex = (fy / 2) * vStride + (fx / 2) * vPixelStride
                    
                    val oldU = uPlane.get(uIndex).toInt() and 0xFF
                    val newU = ((uVal * a + oldU * (255 - a)) / 255).toByte()
                    uPlane.put(uIndex, newU)

                    val oldV = vPlane.get(vIndex).toInt() and 0xFF
                    val newV = ((vVal * a + oldV * (255 - a)) / 255).toByte()
                    vPlane.put(vIndex, newV)
                }
            }
        }
    }
}

/**
 * Robustly crops, scales, and rotates incoming Camera `Image` buffers into exactly the requested 
 * target bounding box with an NV12 planar layout for media encoders.
 *
 * Nearest-neighbor interpolation ensures 30fps budget limits aren't violated.
 */
private fun processCameraImageToNV12(
    image: android.media.Image, 
    targetWidth: Int, 
    targetHeight: Int, 
    sensorOrientation: Int
): ByteArray {
    val srcWidth = image.width
    val srcHeight = image.height

    val srcY = image.planes[0]
    val srcU = image.planes[1]
    val srcV = image.planes[2]

    val yRowStride = srcY.rowStride
    val yPixelStride = srcY.pixelStride
    val uvRowStride = srcU.rowStride
    val uvPixelStride = srcU.pixelStride
    
    val yBytes = ByteArray(srcY.buffer.remaining())
    srcY.buffer.position(0)
    srcY.buffer.get(yBytes)
    
    val uBytes = ByteArray(srcU.buffer.remaining())
    srcU.buffer.position(0)
    srcU.buffer.get(uBytes)

    val vBytes = ByteArray(srcV.buffer.remaining())
    srcV.buffer.position(0)
    srcV.buffer.get(vBytes)

    val isRotated = sensorOrientation == 90 || sensorOrientation == 270
    val rotatedSrcWidth = if (isRotated) srcHeight else srcWidth
    val rotatedSrcHeight = if (isRotated) srcWidth else srcHeight

    val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
    val srcAspect = rotatedSrcWidth.toFloat() / rotatedSrcHeight.toFloat()

    var cropWidth = rotatedSrcWidth
    var cropHeight = rotatedSrcHeight

    if (srcAspect > targetAspect) {
        cropWidth = (rotatedSrcHeight * targetAspect).toInt()
    } else {
        cropHeight = (rotatedSrcWidth / targetAspect).toInt()
    }

    val cropStartX = (rotatedSrcWidth - cropWidth) / 2
    val cropStartY = (rotatedSrcHeight - cropHeight) / 2

    val outData = ByteArray(targetWidth * targetHeight * 3 / 2)
    var outYPos = 0
    var outUVPos = targetWidth * targetHeight
    
    val scaleX = cropWidth.toFloat() / targetWidth
    val scaleY = cropHeight.toFloat() / targetHeight

    when (sensorOrientation) {
        90 -> {
            for (y in 0 until targetHeight) {
                val cy = cropStartY + (y * scaleY).toInt()
                val sx = cy
                for (x in 0 until targetWidth) {
                    val cx = cropStartX + (x * scaleX).toInt()
                    val sy = srcHeight - 1 - cx
                    
                    val yIndex = (sy * yRowStride + sx * yPixelStride).coerceIn(0, yBytes.size - 1)
                    outData[outYPos++] = yBytes[yIndex]
                    
                    if (y % 2 == 0 && x % 2 == 0) {
                        val uvX = sx / 2
                        val uvY = sy / 2
                        val uvIndex = (uvY * uvRowStride + uvX * uvPixelStride).coerceIn(0, uBytes.size - 1)
                        val vIndex = (uvY * uvRowStride + uvX * uvPixelStride).coerceIn(0, vBytes.size - 1)
                        // NV12 requires U then V
                        outData[outUVPos] = uBytes[uvIndex]
                        outData[outUVPos+1] = vBytes[vIndex]
                        outUVPos += 2
                    }
                }
            }
        }
        270 -> {
            for (y in 0 until targetHeight) {
                val cy = cropStartY + (y * scaleY).toInt()
                val sx = srcWidth - 1 - cy
                for (x in 0 until targetWidth) {
                    val cx = cropStartX + (x * scaleX).toInt()
                    val sy = cx
                    
                    val yIndex = (sy * yRowStride + sx * yPixelStride).coerceIn(0, yBytes.size - 1)
                    outData[outYPos++] = yBytes[yIndex]
                    
                    if (y % 2 == 0 && x % 2 == 0) {
                        val uvX = sx / 2
                        val uvY = sy / 2
                        val uvIndex = (uvY * uvRowStride + uvX * uvPixelStride).coerceIn(0, uBytes.size - 1)
                        val vIndex = (uvY * uvRowStride + uvX * uvPixelStride).coerceIn(0, vBytes.size - 1)
                        outData[outUVPos] = uBytes[uvIndex]
                        outData[outUVPos+1] = vBytes[vIndex]
                        outUVPos += 2
                    }
                }
            }
        }
        180 -> {
            for (y in 0 until targetHeight) {
                val cy = cropStartY + (y * scaleY).toInt()
                val sy = srcHeight - 1 - cy
                for (x in 0 until targetWidth) {
                    val cx = cropStartX + (x * scaleX).toInt()
                    val sx = srcWidth - 1 - cx
                    
                    val yIndex = (sy * yRowStride + sx * yPixelStride).coerceIn(0, yBytes.size - 1)
                    outData[outYPos++] = yBytes[yIndex]
                    
                    if (y % 2 == 0 && x % 2 == 0) {
                        val uvX = sx / 2
                        val uvY = sy / 2
                        val uvIndex = (uvY * uvRowStride + uvX * uvPixelStride).coerceIn(0, uBytes.size - 1)
                        val vIndex = (uvY * uvRowStride + uvX * uvPixelStride).coerceIn(0, vBytes.size - 1)
                        outData[outUVPos] = uBytes[uvIndex]
                        outData[outUVPos+1] = vBytes[vIndex]
                        outUVPos += 2
                    }
                }
            }
        }
        else -> { // 0
            for (y in 0 until targetHeight) {
                val cy = cropStartY + (y * scaleY).toInt()
                val sy = cy
                for (x in 0 until targetWidth) {
                    val cx = cropStartX + (x * scaleX).toInt()
                    val sx = cx
                    
                    val yIndex = (sy * yRowStride + sx * yPixelStride).coerceIn(0, yBytes.size - 1)
                    outData[outYPos++] = yBytes[yIndex]
                    
                    if (y % 2 == 0 && x % 2 == 0) {
                        val uvX = sx / 2
                        val uvY = sy / 2
                        val uvIndex = (uvY * uvRowStride + uvX * uvPixelStride).coerceIn(0, uBytes.size - 1)
                        val vIndex = (uvY * uvRowStride + uvX * uvPixelStride).coerceIn(0, vBytes.size - 1)
                        outData[outUVPos] = uBytes[uvIndex]
                        outData[outUVPos+1] = vBytes[vIndex]
                        outUVPos += 2
                    }
                }
            }
        }
    }
    return outData
}
