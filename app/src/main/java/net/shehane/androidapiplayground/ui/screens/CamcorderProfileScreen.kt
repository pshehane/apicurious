package net.shehane.androidapiplayground.ui.screens

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.CamcorderProfile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.shehane.androidapiplayground.ui.components.FeatureScaffold

@Composable
fun CamcorderProfileScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var outputText by remember { mutableStateOf("Press Query to view Camcorder Profiles") }

    fun queryProfiles() {
        val sb = StringBuilder()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            cameraManager.cameraIdList.forEach { cameraId ->
                sb.append("\n=== Camera ID: $cameraId ===\n")
                
                // Intentionally converting string ID to Int for CamcorderProfile, 
                // handling the numeric standard assumption.
                val idInt = cameraId.toIntOrNull()
                if (idInt != null) {
                    val qualities = listOf(
                        "LOW" to CamcorderProfile.QUALITY_LOW,
                        "HIGH" to CamcorderProfile.QUALITY_HIGH,
                        "QCIF" to CamcorderProfile.QUALITY_QCIF,
                        "CIF" to CamcorderProfile.QUALITY_CIF,
                        "480P" to CamcorderProfile.QUALITY_480P,
                        "720P" to CamcorderProfile.QUALITY_720P,
                        "1080P" to CamcorderProfile.QUALITY_1080P,
                        "2160P" to CamcorderProfile.QUALITY_2160P,
                        "4KDCI" to CamcorderProfile.QUALITY_4KDCI,
                        "QHD" to CamcorderProfile.QUALITY_QHD,
                        "2K" to CamcorderProfile.QUALITY_2K,
                        "8K" to CamcorderProfile.QUALITY_8KUHD,
                        "TIME_LAPSE_LOW" to CamcorderProfile.QUALITY_TIME_LAPSE_LOW,
                        "TIME_LAPSE_HIGH" to CamcorderProfile.QUALITY_TIME_LAPSE_HIGH,
                        "HIGH_SPEED_LOW" to CamcorderProfile.QUALITY_HIGH_SPEED_LOW,
                        "HIGH_SPEED_HIGH" to CamcorderProfile.QUALITY_HIGH_SPEED_HIGH
                    )

                    qualities.forEach { (name, quality) ->
                        if (CamcorderProfile.hasProfile(idInt, quality)) {
                            // Using the newer getAll approach if available/applicable, but standard get is still common for single profile
                            // For simplicity and broad support, strictly displaying the profile data
                            val profile = CamcorderProfile.get(idInt, quality)
                            sb.append("  Profile: $name\n")
                            sb.append("    Format: ${profile.fileFormat}\n")
                            sb.append("    Video: ${profile.videoCodec} | ${profile.videoFrameWidth}x${profile.videoFrameHeight} | ${profile.videoFrameRate}fps | ${profile.videoBitRate}bps\n")
                            sb.append("    Audio: ${profile.audioCodec} | ${profile.audioSampleRate}Hz | ${profile.audioBitRate}bps | ${profile.audioChannels}ch\n")
                            sb.append("    ----------------\n")
                        }
                    }
                } else {
                     sb.append("  (Skipping non-numeric Camera ID)\n")
                }
            }
        } catch (e: Exception) {
            sb.append("Error querying profiles: ${e.message}")
        }
        outputText = sb.toString()
    }

    FeatureScaffold(
        title = "Camcorder Profile",
        onBackClick = onBackClick,
        controlsContent = {
            Button(onClick = { queryProfiles() }) {
                Text("Query Profiles")
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    )
}
