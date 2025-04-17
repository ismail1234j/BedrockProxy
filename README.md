# Bedrock Relay V2 (Phantom Android Wrapper)

This is an Android application that serves as a wrapper for the `jhead/phantom` proxy. It allows you to run and manage the phantom proxy service directly on your Android device.

## Features

*   Provides a simple UI to start and stop the phantom proxy service.
*   Runs phantom as a foreground service to ensure it keeps running in the background.
*   Displays logs from the running phantom process within the app.
*   Handles necessary permissions and manages the phantom binary lifecycle.

## Purpose

This application wraps the `phantom` executable, which acts as a proxy, likely intended for use with Minecraft Bedrock Edition servers, allowing connections or modifications based on phantom's capabilities.

## Usage

1.  Build and install the application on your Android device.
2.  Launch the app.
3.  Enter any required command-line arguments for phantom in the input field.
4.  Tap the "Start" button to initiate the proxy service.
5.  Tap the "Stop" button to terminate the service.
6.  Logs from the phantom process will be displayed in the text area.

## Building

This is a standard Android project using Gradle. You can build it using Android Studio or via the command line:

```bash
./gradlew assembleDebug
# or
./gradlew assembleRelease
```

Ensure you have the necessary Android SDK components installed. The phantom binary (`libphantom.so`) should be placed in the appropriate `jniLibs` folder (`app/src/main/jniLibs/<abi>/`) for the target architecture(s).
