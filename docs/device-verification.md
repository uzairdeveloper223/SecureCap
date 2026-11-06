# Device verification

Tested on **November 6, 2026**.

| Field | Value |
| --- | --- |
| Device | OnePlus 8T, KB2007 |
| Android | 11 / API 30 |
| Build | RP1A.201005.001 / 2101212317 |
| Shizuku | Running as ADB shell, UID 2000 |
| Test app | `dev.uzair.flagtest` |

## Engine results

- WindowManager reported the test window's `SECURE` flag.
- The secure-display screenshot produced a **1080 × 2400 PNG** with readable `FLAG_SECURE` text and the test counter.
- The MP4 recording produced readable test-screen frames at **720 × 1600**.
- `ffprobe` identified **H.264 video** and **48 kHz stereo AAC audio**.
- A roughly two-second pause/resume interval was removed from the output timeline. The resulting file had approximately **7.34 seconds of video** and **7.49 seconds of audio**.
- The recording was decodable with FFmpeg.
- Audio initialization failed while the device was locked and succeeded after unlocking. The controlled engine run had no audible playback source, so an encoded audio track alone does not establish audible-content capture.

## App results

The app connected to its Shizuku shell user service. Uzair subsequently tested the installed app and reported that it worked well on this device.

These results establish this device's FLAG_SECURE behavior. Other ROMs and hardware-protected DRM pipelines require their own verification.
