package net.shehane.androidapiplayground.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.shehane.androidapiplayground.ui.Screen

@Composable
fun HomeScreen(
    onNavigateTo: (String) -> Unit
) {
    val features = listOf(
        Screen.CameraFrameRates,
        Screen.CamcorderProfile,
        Screen.CodecsAvailable,
        Screen.Intents,
        Screen.Benchmarks
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Text(
                text = "API Curious",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(24.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(features) { feature ->
                    Button(
                        onClick = { onNavigateTo(feature.route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                           text = feature.title,
                           style = MaterialTheme.typography.titleMedium,
                           modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
