# MonkeyRemote
Remote control your Android device via ADB

Warning: The screen updates are very slow (~1 frame per second).

## Build
    mvn package

## Usage
Run with:

    java -jar MonkeyRemote-0.1.jar "PATH TO ADB EXECUTABLE" SCALING_FACTOR

so for example:

    java -jar MonkeyRemote-0.1.jar "C:\android-sdk\platform-tools\adb.exe" 0.5

