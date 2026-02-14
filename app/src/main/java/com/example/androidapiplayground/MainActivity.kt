package com.example.androidapiplayground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.androidapiplayground.ui.Screen
import com.example.androidapiplayground.ui.screens.BenchmarksScreen
import com.example.androidapiplayground.ui.screens.CamcorderProfileScreen
import com.example.androidapiplayground.ui.screens.CameraFrameRatesScreen
import com.example.androidapiplayground.ui.screens.CodecsAvailableScreen
import com.example.androidapiplayground.ui.screens.HomeScreen
import com.example.androidapiplayground.ui.screens.IntentsScreen
import com.example.androidapiplayground.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController, 
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(onNavigateTo = { route -> navController.navigate(route) })
                        }
                        composable(Screen.CameraFrameRates.route) {
                            CameraFrameRatesScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.CamcorderProfile.route) {
                            CamcorderProfileScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.CodecsAvailable.route) {
                            CodecsAvailableScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Intents.route) {
                            IntentsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.Benchmarks.route) {
                            BenchmarksScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
