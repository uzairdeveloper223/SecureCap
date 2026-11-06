# Contributing to SecureCap

Thanks for helping improve SecureCap.

## Development

Use JDK 17 and Android SDK platform 35, then run:

```sh
./gradlew assembleDebug lintDebug
```

Keep the UI consistent with the native views, palette, and icon set in `app/src/main/java/com/uzair/securecap/ui/`. Check both light and dark appearance, including system-follow mode, dialogs, and media previews.

For capture changes, test on a real Android 11 device with Shizuku running as shell. Verify start/stop, pause/resume, screenshots, playable MP4 output, and audio with a known sound source. The ADB probe in the README can help isolate engine behavior from the UI.

## Reporting an issue

Include:

- Device model, Android version/API, and ROM/build version.
- SecureCap version and Shizuku version/start method.
- Steps to reproduce and the expected/actual result.
- Audio source, resolution, frame rate, and bitrate where relevant.
- Relevant `SecureCap` logcat lines and, if useful, `ffprobe` output from a test capture.

Use a reproducible test screen rather than a personal recording when attaching sample files.

## Pull requests

Describe the change and the checks you ran. Keep changes focused and update documentation when behavior changes. Contributions are accepted under the project's MIT license.
