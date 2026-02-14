package com.example.androidapiplayground.ui.screens

import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidapiplayground.ui.components.FeatureScaffold

@Composable
fun CodecsAvailableScreen(
    onBackClick: () -> Unit
) {
    var outputText by remember { mutableStateOf("Press Query to view Available Codecs") }
    var showHW by remember { mutableStateOf(true) }
    var showSW by remember { mutableStateOf(true) }
    var vendorOnly by remember { mutableStateOf(false) }

    fun queryCodecs() {
        val sb = StringBuilder()
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val codecInfos = codecList.codecInfos

            // Filter logic
            val filteredCodecs = codecInfos.filter { info ->
                val isHW = info.isHardwareAccelerated
                val isSW = info.isSoftwareOnly
                val isVendor = info.isVendor

                val typeMatch = (showHW && isHW) || (showSW && isSW)
                // If neither HW nor SW is selected, show nothing (or everything? Logic above implies nothing if both false)
                // Actually, if a codec is neither HW nor SW (unlikely in API 29+), it won't show.
                // Assuming every codec is either HW or SW.

                val vendorMatch = if (vendorOnly) isVendor else true
                
                typeMatch && vendorMatch
            }

            sb.append("Total Codecs Found: ${filteredCodecs.size} (of ${codecInfos.size})\n")
            sb.append("Filters: HW=$showHW, SW=$showSW, VendorOnly=$vendorOnly\n\n")

            filteredCodecs.sortedBy { it.name }.forEach { info ->
                sb.append("Name: ${info.name}\n")
                sb.append("Type: ${if (info.isEncoder) "Encoder" else "Decoder"}\n")
                sb.append("HW/SW: ${if (info.isHardwareAccelerated) "Hardware" else "Software"}\n")
                sb.append("Vendor: ${info.isVendor}\n")
                sb.append("Supported Types: ${info.supportedTypes.joinToString(", ")}\n")
                
                // Detailed Capabilities for first supported type
                if (info.supportedTypes.isNotEmpty()) {
                    try {
                        val type = info.supportedTypes[0]
                        val caps = info.getCapabilitiesForType(type)
                        sb.append("  Max Instances: ${caps.maxSupportedInstances}\n")
                        
                        // Video Capabilities
                        val videoCaps = caps.videoCapabilities
                        if (videoCaps != null) {
                            sb.append("  Bitrate Range: ${videoCaps.bitrateRange}\n")
                            sb.append("  Frame Rates: ${videoCaps.supportedFrameRates}\n")
                            sb.append("  Width Alignment: ${videoCaps.widthAlignment}\n")
                            sb.append("  Height Alignment: ${videoCaps.heightAlignment}\n")
                        }
                        
                        // Audio Capabilities
                        val audioCaps = caps.audioCapabilities
                        if (audioCaps != null) {
                            sb.append("  Bitrate Range: ${audioCaps.bitrateRange}\n")
                            sb.append("  Sample Rates: ${audioCaps.supportedSampleRateRanges.joinToString()}\n")
                        }
                    } catch (e: Exception) {
                        sb.append("  (Error reading capabilities)\n")
                    }
                }
                sb.append("------------------------------------------------\n")
            }

        } catch (e: Exception) {
            sb.append("Error querying codecs: ${e.message}")
        }
        outputText = sb.toString()
    }

    FeatureScaffold(
        title = "Codecs Available",
        onBackClick = onBackClick,
        controlsContent = {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Button(
                    onClick = { queryCodecs() },
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Text("Query")
                }

                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = showHW,
                        onCheckedChange = { showHW = it }
                    )
                    Text("Show HW")
                }
                
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = showSW,
                        onCheckedChange = { showSW = it }
                    )
                    Text("Show Only SW") 
                }
                
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = vendorOnly,
                        onCheckedChange = { vendorOnly = it }
                    )
                    Text("Vendor Only")
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
                    style = MaterialTheme.typography.bodySmall, // Smaller font for dense info
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    )
}
