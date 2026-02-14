# API Curious: Android API Playground

**API Curious** is an Android application designed for exploring and understanding specific Camera, Audio, and Video APIs. It serves as a hands-on playground to test device capabilities, run benchmarks, and experiment with media APIs directly on your phone.

## Features

### 1. Camera Frame Rates
*   **Explore FPS Ranges**: View supported minimum and maximum frame rate ranges (e.g., [15, 30], [30, 60]) for each camera device (Front/Back).
*   **Session Configuration**: Create actual capture sessions with selected FPS ranges using `SessionConfiguration` (API 29+) to verify real-world behavior.
*   **Modern API Usage**: Demonstrates correct usage of `Camera2` API and session state callbacks.

### 2. Camcorder Profiles
*   **Profile Inspection**: detailed view of all supported `CamcorderProfile` qualities (e.g., 8K, 4K, 1080p, High Speed 240fps).
*   **Deep Dive**: Displays detailed information for each profile including:
    *   **Video**: Codec (HEVC, H.264, VP9), Resolution, Bitrate, Frame Rate.
    *   **Audio**: Codec (AAC, Opus, AMR), Sample Rate, Bitrate, Channels.
*   **Modern Implementation**: Uses the newer `EncoderProfiles` (API 31+) to correctly handle profiles with multiple video/audio streams, while maintaining backward compatibility.

### 3. Codec Query
*   **Device Discovery**: List all available media codecs (Encoders & Decoders) on the device using `MediaCodecList`.
*   **Smart Filtering**:
    *   **Hardware vs Software**: Toggle to see only hardware-accelerated codecs or software implementations.
    *   **Vendor Specific**: Filter for vendor-provided codecs (e.g., Qualcomm, Samsung, Google).
*   **Detailed Specs**: Shows MIME types and canonical names.

### 4. Intents Playground
*   **System Integration**: Test standard Android Intents for media capture.
    *   **Capture Image**: Launch camera to take a photo.
    *   **Capture Video**: Launch camera to record a video.
    *   **Record Sound**: Launch sound recorder.
*   **Public Storage**: Automatically saves captured media to the device's public Gallery (`MediaStore`) for easy access.
*   **Photo Picker**: Demonstrates the modern, privacy-preserving Android Photo Picker.

### 5. AV1 & HEVC Benchmarks
*   **Performance Testing**: Run benchmarks to compare AV1 and HEVC decoding performance on your specific hardware.
*   **Test Generation**: Built-in tool to generate standardized test video files (1080p, 5 sec loop) using device encoders.
*   **Data Export**: Benchmarking results are saved to a JSON file (`benchmark_results.json`) in external storage for analysis.

## Tech Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material3)
*   **Architecture**: Single Activity (Edge-to-Edge)
*   **Key APIs**:
    *   `camera2`
    *   `media` (`MediaCodec`, `CamcorderProfile`, `EncoderProfiles`)
    *   `MediaStore` & `FileProvider`
*   **Min SDK**: 24 (Android 7.0) - *Note: Some features require newer APIs but gracefully degrade.*
*   **Target SDK**: 35 (Android 15)

## Setup & Run

1.  **Clone the repository**.
2.  Open in **Android Studio**.
3.  Sync Gradle project.
4.  Run on an Android device or emulator.
    *   *Note: Physical devices recommended for accurate hardware codec and camera testing.*

## License

This project is for educational purposes.
