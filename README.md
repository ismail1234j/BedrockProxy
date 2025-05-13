# Bedrock Proxy

## It's probably better to use a DNS method for consoles as this app has issues with battery management on certain devices.

An Android application that serves as a wrapper for `jhead/phantom`. It allows you to have your consoles join a server through LAN Games.

## Features

*   Provides a simple UI to start and stop the phantom proxy service.
*   Runs phantom as a foreground service to ensure it keeps running in the background.
*   Displays logs from the running phantom process within the app.
*   Handles necessary permissions and manages the phantom binary lifecycle.
*   Auto Dark/light Theme.
*   Works for Bedrock Release AND Preview (Beta) editions.

## How-To

1.  Download the latest build from [here](https://github.com/ismail1234j/bedrockproxyv2/releases)  or you can build yourself by following [this](#building).

2.  Install the app to your phone.

3. Now you can use the Phantom app as you would on a desktop computer. Here is an example command to enter into the app: `-server lax.mcbr.cubed.host:19132`. If you need help with the arguments to enter see the [Phantom Repository here.](https://github.com/jhead/phantom?tab=readme-ov-file#usage)

## Contributions

All and any contributions are welcome, just open a pr and I'll have a look.
## Building

It's easiest to use the latest release but incase I haven't made one and there is a game breaking change follow this guide:

1. You need to download the latest Phantom Binary from jhead's repo. [Here](https://github.com/jhead/phantom/releases).
    
2. Make sure its the `phantom-linux-arm8` and the `phantom-linux-arm7` that you are downloading.
    
3.  Then from the root directory of this project go to `app/src/main/jniLibs` folder (the jniLibs folder may or may not be there so you might have to create it).

4. Now you create two folder, `arm64-v8a` and `armeabi-v7a`.
    
5. The `phantom-linux-arm8` file from earlier needs to go into `arm64-v8a` and the `phantom-linux-arm7` file needs to go into `armeabi-v8a`. 
    
6. Once done you need to rename both binaries into `libphantom.so`.
    
7. Then you can build with [Android Studio](https://developer.android.com/studio) or with this command: `./gradlew assembleDebug`.
