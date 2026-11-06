# Android 11 secure capture research

## FLAG_SECURE propagation

WindowManager propagates `WindowManager.LayoutParams.FLAG_SECURE` to the window's surface secure flag. SurfaceFlinger marks the layer as secure; its composition path blacks out secure layers on an insecure output. A display's secure property is therefore significant independently of screenshot permissions.

- [Android 11 WindowStateAnimator](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-11.0.0_r1/services/core/java/com/android/server/wm/WindowStateAnimator.java)
- [Android 11 SurfaceControl](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-11.0.0_r1/core/java/android/view/SurfaceControl.java)
- [Android 11 SurfaceFlinger](https://android.googlesource.com/platform/frameworks/native/+/refs/tags/android-11.0.0_r1/services/surfaceflinger/SurfaceFlinger.cpp)
- [Android 11 composition Output](https://android.googlesource.com/platform/frameworks/native/+/refs/tags/android-11.0.0_r1/services/surfaceflinger/CompositionEngine/src/Output.cpp)

## Why scrcpy works on Android 11

scrcpy creates a display through hidden `SurfaceControl.createDisplay(name, secure)` with `secure=true` on released Android 11. It projects display 0's layer stack into a hardware encoder input surface. This is not a MediaProjection session. SecureCap implements this capture topology inside a Shizuku user service running as the shell UID.

- [scrcpy v2 ScreenEncoder: createDisplay and projection](https://github.com/Genymobile/scrcpy/blob/v2.0/server/src/main/java/com/genymobile/scrcpy/ScreenEncoder.java)
- [scrcpy SurfaceControl reflection wrapper](https://github.com/Genymobile/scrcpy/blob/v2.0/server/src/main/java/com/genymobile/scrcpy/wrappers/SurfaceControl.java)
- [Android 12 SurfaceFlinger restriction](https://android.googlesource.com/platform/frameworks/native/+/refs/tags/android-12.0.0_r1/services/surfaceflinger/SurfaceFlinger.cpp): `createDisplay` rejects secure requests unless the caller is `AID_GRAPHICS` or `AID_SYSTEM`.

Android 11's ordinary screenshot path separately rejects capture of visible secure layers for non-system callers. Consequently SecureCap screenshots use **ImageReader on a secure display**, not `screencap` or the ordinary screenshot API.

## Device audio

`FLAG_SECURE` is a window/video flag, not an audio policy. scrcpy captures device output with `AudioRecord` and `REMOTE_SUBMIX`, attributed to `com.android.shell`. On Android 11 it briefly launches `com.android.shell.HeapDumpActivity` before starting AudioRecord, then force-stops that shell activity. This satisfies Android 11's foreground requirement for the shared shell UID. AudioCapture's path is distinct from playback capture via MediaProjection, where apps may opt out.

- [scrcpy AudioCapture](https://github.com/Genymobile/scrcpy/blob/v2.0/server/src/main/java/com/genymobile/scrcpy/AudioCapture.java)
- [scrcpy Workarounds](https://github.com/Genymobile/scrcpy/blob/v2.0/server/src/main/java/com/genymobile/scrcpy/Workarounds.java)
- [scrcpy audio documentation](https://github.com/Genymobile/scrcpy/blob/master/doc/audio.md)
- [Shizuku UserService guide](https://github.com/RikkaApps/Shizuku-API#userservice)
- [Android MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer)

SecureCap passes writable MediaStore file descriptors from the app to the shell service over Binder. The shell service does not receive access to arbitrary app-private storage. The app handles pending entries and publishes only finalized output.

## Boundaries established by the sources

- The Android 11 secure-display path can include FLAG_SECURE windows on compatible ROMs.
- Shizuku provides shell identity, not system identity, and does not restore this Android 11 behavior on Android 12+.
- Hardware-protected buffers, DRM pipelines, vendor changes, and audio routing are independent constraints.
- Actual support must be established on the connected device using the user's own test application.
