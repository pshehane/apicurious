package com.example.androidapiplayground.ui.screens

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
import com.example.androidapiplayground.ui.components.FeatureScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IntentsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf("Ready to capture media") }

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
            statusMessage = "Image captured to Gallery: ${lastCapturedUri?.lastPathSegment}"
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
            statusMessage = "Video captured to Gallery: ${lastCapturedUri?.lastPathSegment}"
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
                statusMessage = "Audio recorded: $uri"
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
            statusMessage = "Picked Media: $uri"
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
