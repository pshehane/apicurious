package net.shehane.androidapiplayground.ui

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "API Curious")
    object CameraFrameRates : Screen("camera_framerates", "API: Camera FrameRates")
    object CamcorderProfile : Screen("camcorder_profile", "API: Camcorder Profile")
    object CodecsAvailable : Screen("codecs_available", "API: Codecs Available")
    object Intents : Screen("intents", "API: Intents Playground")
    object Benchmarks : Screen("benchmarks", "API: AV1 & HEVC Benchmarks")
}
