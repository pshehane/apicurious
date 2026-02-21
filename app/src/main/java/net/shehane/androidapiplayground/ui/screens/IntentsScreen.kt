package net.shehane.androidapiplayground.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import net.shehane.androidapiplayground.ui.components.FeatureScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun IntentsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf("Ready to capture media") }

    suspend fun extractMediaInfo(uri: Uri): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("Picked Media: $uri\n\n")

        // 1. File size / name from DocumentFile / Cursor
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) sb.append("Name: ${cursor.getString(nameIndex)}\n")
                if (sizeIndex >= 0) {
                    val sizeBytes = cursor.getLong(sizeIndex)
                    sb.append("Size: ${sizeBytes / 1024} KB\n")
                }
            }
        }

        // 2. MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            
            sb.append("Type: ${if (hasVideo == "yes") "Video" else if (hasAudio == "yes") "Audio" else "Unknown"}\n")
            
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let { w ->
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                sb.append("Resolution: ${w}x${h}\n")
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { 
                sb.append("Bitrate: ${it.toLong() / 1000} kbps\n") 
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let { 
                sb.append("Duration: ${it.toLong() / 1000} seconds\n") 
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { 
                sb.append("MimeType (Retriever): $it\n") 
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { 
                sb.append("Date: $it\n") 
            }
        } catch (e: Exception) {
            sb.append("Metadata Retriever Error: ${e.message}\n")
        } finally {
            retriever.release()
        }

        // 3. MediaExtractor (for specific codec formats string)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            sb.append("\nTracks:\n")
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: "unknown"
                val typeDetails = if (mime.startsWith("video/")) {
                    val w = if (format.containsKey(android.media.MediaFormat.KEY_WIDTH)) format.getInteger(android.media.MediaFormat.KEY_WIDTH) else 0
                    val h = if (format.containsKey(android.media.MediaFormat.KEY_HEIGHT)) format.getInteger(android.media.MediaFormat.KEY_HEIGHT) else 0
                    "${w}x${h}"
                } else if (mime.startsWith("audio/")) {
                    val sampleRate = if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) else 0
                    val channels = if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) else 0
                    "${sampleRate}Hz, ${channels}ch"
                } else ""
                
                sb.append("- Track $i: $mime ($typeDetails)\n")
            }
        } catch (e: Exception) {
            sb.append("Extractor Error: ${e.message}\n")
        } finally {
            extractor.release()
        }

        sb.toString()
    }

    // Permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cameraGranted = perms[Manifest.permission.CAMERA] ?: false
        val audioGranted = perms[Manifest.permission.RECORD_AUDIO] ?: false
        statusMessage = "Permissions updated: Camera=$cameraGranted, Audio=$audioGranted"
    }

    fun checkAndRequestPermissions(permissions: Array<String>, onGranted: () -> Unit) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onGranted()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // MediaStore Helper
    fun createMediaStoreUri(type: String): Uri? {
        val contentValues = ContentValues().apply {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${type}_$timeStamp")
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis())
            when (type) {
                "image" -> {
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ApiPlayground")
                }
                "video" -> {
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ApiPlayground")
                }
            }
        }
        val collection = when(type) {
            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }
        return context.contentResolver.insert(collection, contentValues)
    }

    // Image Capture Helper
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            lastCapturedUri = tempImageUri
            scope.launch {
                lastCapturedUri?.let { statusMessage = extractMediaInfo(it) }
            }
            Toast.makeText(context, "Image Saved directly to Gallery", Toast.LENGTH_SHORT).show()
        } else {
            statusMessage = "Image capture failed or cancelled"
            // Cleanup empty uri?
            tempImageUri?.let { context.contentResolver.delete(it, null, null) }
        }
    }

    // Video Capture Helper
    var tempVideoUri by remember { mutableStateOf<Uri?>(null) }
    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            lastCapturedUri = tempVideoUri
            scope.launch {
                lastCapturedUri?.let { statusMessage = extractMediaInfo(it) }
            }
            Toast.makeText(context, "Video Saved directly to Gallery", Toast.LENGTH_SHORT).show()
        } else {
            statusMessage = "Video capture failed or cancelled"
             // Cleanup empty uri?
             tempVideoUri?.let { context.contentResolver.delete(it, null, null) }
        }
    }

    // Audio Capture Helper
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                lastCapturedUri = uri
                scope.launch {
                    statusMessage = extractMediaInfo(uri)
                }
                Toast.makeText(context, "Audio Saved", Toast.LENGTH_SHORT).show()
            }
        } else {
            statusMessage = "Audio recording failed or cancelled"
        }
    }

    // Photo Picker Helper
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            lastCapturedUri = uri
            scope.launch {
                statusMessage = extractMediaInfo(uri)
            }
            Toast.makeText(context, "Media Picked", Toast.LENGTH_SHORT).show()
        } else {
            statusMessage = "No media picked"
        }
    }

    FeatureScaffold(
        title = "Intents Playground",
        onBackClick = onBackClick,
        controlsContent = {
           // No specific controls in the top bar for this one, using main buttons
        },
        outputContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = statusMessage)

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        checkAndRequestPermissions(arrayOf(Manifest.permission.CAMERA)) {
                            val uri = createMediaStoreUri("image")
                            if (uri != null) {
                                tempImageUri = uri
                                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                    putExtra(MediaStore.EXTRA_OUTPUT, uri)
                                    // Tricks to force rear camera
                                    putExtra("android.intent.extras.CAMERA_FACING", 0) // 0 is back
                                    putExtra("android.intent.extras.LENS_FACING_FRONT", 0)
                                    putExtra("android.intent.extra.USE_FRONT_CAMERA", false)
                                    putExtra("com.google.assistant.extra.USE_FRONT_CAMERA", false)
                                }
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    takePictureLauncher.launch(intent)
                                } else {
                                    Toast.makeText(context, "No Camera App Found", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Error creating MediaStore entry", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Capture Image (Permissions + Gallery)")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        checkAndRequestPermissions(arrayOf(Manifest.permission.CAMERA)) {
                            val uri = createMediaStoreUri("video")
                            if (uri != null) {
                                tempVideoUri = uri
                                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                                    putExtra(MediaStore.EXTRA_OUTPUT, uri)
                                    // Tricks to force rear camera
                                    putExtra("android.intent.extras.CAMERA_FACING", 0)
                                    putExtra("android.intent.extras.LENS_FACING_FRONT", 0)
                                    putExtra("android.intent.extra.USE_FRONT_CAMERA", false)
                                    putExtra("com.google.assistant.extra.USE_FRONT_CAMERA", false)
                                }
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    captureVideoLauncher.launch(intent)
                                } else {
                                    Toast.makeText(context, "No Camera App Found", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Error creating MediaStore entry", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Capture Video (Permissions + Gallery)")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        checkAndRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO)) {
                            val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
                            // Note: Audio intent varies widely by device, permissions are key though.
                            if (intent.resolveActivity(context.packageManager) != null) {
                                recordAudioLauncher.launch(intent)
                            } else {
                                Toast.makeText(context, "No Audio Recorder Found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Record Sound (Permissions)")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                ) {
                    Text("Pick Visual Media (Photo Picker)")
                }
            }
        }
    )
}
