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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.shehane.androidapiplayground.ui.components.FeatureScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

@Composable
fun BenchmarksScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var outputText by remember { mutableStateOf("Ready to Benchmark AV1 & HEVC") }
    
    // File paths
    val av1File = File(context.filesDir, "test_av1.mp4")
    val hevcFile = File(context.filesDir, "test_hevc.mp4")

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
                    val bitRate = 2000000 // 2Mbps

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
                                    // Generate YUV Frame
                                    // Simple pattern: Moving vertical bar or changing luma
                                    // For simplicity: Flat color that changes over time
                                    val ySize = width * height
                                    val uvSize = width * height / 4
                                    val frameSize = ySize + uvSize * 2
                                    
                                    // This assumes semi-planar or planar depending on device, but Flexible handles retrieval via Image usually.
                                    // InputBuffer is just ByteBuffer. For Flexible, we assume standard NV12 or I420 layout in buffer?
                                    // Actually, for direct ByteBuffer access, we should fill it carefully.
                                    // MIGHT BE RISKY without Image interface. But typical encoders accept NV12/YUV420P in the byte buffer linearly.
                                    // Let's generate a gray frame with changing brightness.
                                    
                                    val loops = 5 // 5 second loop? No, 30 frame loop over 150 frames = 5 loops. 
                                    val t = (inputFrameIndex % 30) / 30.0
                                    val yValue = (t * 255).toInt().toByte()
                                    val uValue = 128.toByte()
                                    val vValue = 128.toByte()

                                    // Fill Y
                                    for (i in 0 until ySize) {
                                        inputBuffer.put(i, yValue)
                                    }
                                    // Fill U/V (Approximation for standard layouts)
                                    // We fill remaining buffer with 128 (neutral chroma)
                                    for (i in ySize until inputBuffer.capacity()) {
                                        inputBuffer.put(i, 128.toByte())
                                    }

                                    val ptr = inputFrameIndex * 1000000L / frameRate
                                    codec.queueInputBuffer(inputBufferId, 0, inputBuffer.capacity(), ptr, 0)
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
                    // Use a simple data class or just string for log to avoid thread issues
                    // But here we are in IO context, so we can't update UI directly inside loop efficiently without flooding.
                    // We'll return status or log after completion.
                    throw RuntimeException("Success: ${timeTaken}ms") // Hacky flow control, but using try/catch wrapper below
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

    fun findDecoders() {
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val decoders = codecList.codecInfos.filter { !it.isEncoder && (it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_AV1) || it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_HEVC)) && !it.name.contains("secure", ignoreCase = true) }
            
            val sb = StringBuilder()
            sb.append("\nFound ${decoders.size} decoders:\n")
            decoders.forEach { info ->
                val type = if (info.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_AV1)) "AV1" else "HEVC"
                val isSw = if (info.isSoftwareOnly) "SW" else "HW"
                sb.append("- ${info.name} ($type, $isSw)\n")
            }
            appendLog(sb.toString())
        } catch (e: Exception) {
            appendLog("Error finding decoders: ${e.message}")
        }
    }

    suspend fun runBenchmarks() {
        withContext(Dispatchers.IO) {
             if (!av1File.exists() || !hevcFile.exists()) {
                withContext(Dispatchers.Main) {
                    appendLog("Files not found. Please Generate Test Pattern first.")
                 }
                return@withContext
            }

            try {
                withContext(Dispatchers.Main) { 
                    outputText = "Running Benchmarks...\n" // Clear previous or append? User request said "showing the user what you are doing"
                    // Let's clear to keep it clean or just append separator.
                    appendLog("--- Starting Benchmark Run ---")
                }

                data class Result(val name: String, val type: String, val timeMs: Long, val frameCount: Int, val fps: Float)

                val results = mutableListOf<Result>()

                // Helper function to benchmark a specific decoder
                suspend fun benchmarkDecoder(decoderName: String, file: File, mimeType: String): Result? {
                    return try {
                         withContext(Dispatchers.Main) { appendLog("Benchmarking $decoderName...") }
                        
                        val extractor = MediaExtractor()
                        extractor.setDataSource(file.absolutePath)
                        
                        var trackIndex = -1
                        var inputFormat: MediaFormat? = null
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            val mime = format.getString(MediaFormat.KEY_MIME)
                            if (mime?.startsWith("video/") == true) {
                                trackIndex = i
                                inputFormat = format
                                break
                            }
                        }

                        if (trackIndex < 0 || inputFormat == null) {
                            extractor.release()
                            throw RuntimeException("No video track found")
                        }

                        extractor.selectTrack(trackIndex)

                        val codec = MediaCodec.createByCodecName(decoderName)
                        codec.configure(inputFormat, null, null, 0) // No Surface
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
                                            codec.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                                            extractor.advance()
                                        }
                                    }
                                }
                            }

                            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000)
                            if (outputBufferId >= 0) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    outputDone = true
                                }
                                codec.releaseOutputBuffer(outputBufferId, false)
                                frameCount++
                            }
                        }
                        
                        val duration = System.currentTimeMillis() - startTime
                        val fps = if (duration > 0) (frameCount * 1000f) / duration else 0f
                        
                        codec.stop()
                        codec.release()
                        extractor.release()

                        Result(decoderName, mimeType, duration, frameCount, fps)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) { appendLog("  Failed: ${e.message}") }
                        null
                    }
                }

                val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
                
                // Find and Run AV1 Decoders
                val av1Decoders = codecList.codecInfos.filter { !it.isEncoder && it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_AV1) && !it.name.contains("secure", ignoreCase = true) }
                av1Decoders.forEach { info ->
                    val res = benchmarkDecoder(info.name, av1File, "AV1")
                    if (res != null) results.add(res)
                }

                // Find and Run HEVC Decoders
                val hevcDecoders = codecList.codecInfos.filter { !it.isEncoder && it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_HEVC) && !it.name.contains("secure", ignoreCase = true) }
                hevcDecoders.forEach { info ->
                    val res = benchmarkDecoder(info.name, hevcFile, "HEVC")
                    if (res != null) results.add(res)
                }

                // Sort and Display
                // Sort and Display
                withContext(Dispatchers.Main) {
                    appendLog("\n--- Results (Fastest to Slowest) ---")
                    results.sortedByDescending { it.fps } // Fastest fps first
                        .forEach { res ->
                            appendLog("${res.name} (${res.type}): %.2f FPS (${res.timeMs}ms)".format(res.fps))
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

    FeatureScaffold(
        title = "AV1 & HEVC Benchmarks",
        onBackClick = onBackClick,
        controlsContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
            ) {
                Button(onClick = { scope.launch { generateTestPattern() } }) {
                    Text("Generate")
                }
                Button(onClick = { findDecoders() }) {
                    Text("Find All")
                }
                Button(onClick = { scope.launch { runBenchmarks() } }) {
                    Text("Run")
                }
            }
        },
        outputContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = outputText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    )
}
