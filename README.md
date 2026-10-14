# Multistream for Android

Go live on **YouTube, Twitch and Kick at the same time, straight from your phone**. It's free and open source, has no subscription, and needs no relay server.

Paid apps usually send your video to their own servers, which copy it out to each platform. This app does the copying on the phone. It encodes your camera once (or twice, see [Two encoders](#two-encoders-how-1440p-works)) and sends that stream to every platform you turn on.

> **Status: v0.1, first build.** Camera + microphone streaming to YouTube, Twitch, Kick and any RTMP/RTMPS server. Tested so far against a local test server, not yet on real devices at scale. Please [file issues](../../issues).

## What it does

- **One tap, every platform.** A setup wizard asks which platforms you use and where to paste each stream key, and that's it.
- **Buttons and dials, not forms.** You only ever paste your stream key (and Kick's per-account server URL). Quality is chosen with preset cards, resolution buttons, frame-rate chips and a bitrate dial that snaps in 500 kbps steps.
- **Platform limits built in.** The app knows each platform's caps. It won't let you pick a setting a platform rejects, and when an option is greyed out, tapping it tells you why ("Kick supports up to 1080p").
- **Each platform is independent.** Every destination has its own status light, retry and stop button. If Kick drops, YouTube and Twitch keep going while Kick reconnects with back-off.
- **Safe connection checks.** "Check connection" only opens a connection to the server, so it never puts you live. Twitch also has a test mode (Twitch's own bandwidth test) that connects without going public.
- **Stream keys stay on the phone,** encrypted with a key from the Android Keystore and excluded from backups.

## Platform limits

| Platform | Max the app sends | Video | Keyframe | Audio | Server |
|---|---|---|---|---|---|
| **YouTube** | 2560×1440 @ 60 | H.264 CBR, **34 000 kbps** (YouTube's H.264 recommendation for 1440p60; minimum 8000) | 2 s (≤ 4) | AAC 128 kbps stereo, 44.1 kHz | RTMPS (recommended) or RTMP, picked from a list |
| **Twitch** | 1920×1080 @ 60 | H.264 CBR, up to 6000 kbps, Main/High profile | 2 s | AAC ≤ 160 kbps | Auto ingest (RTMP or RTMPS), picked from a list; 48-hour max broadcast |
| **Kick** | 1920×1080 @ 60 | H.264 CBR only, 1000–8000 kbps, **Main profile**, no B-frames | 2 s | AAC stereo ≤ 48 kHz | **RTMPS only**; URL is per account, pasted from the Kick dashboard |
| Custom | up to 1440p60 | anything 500–50 000 kbps | 2 s | AAC | any `rtmp://` or `rtmps://` |

All limits live in one file, [`core/.../Platform.kt`](core/src/main/kotlin/com/kelleyparker/multistream/core/Platform.kt), so they're easy to update when platforms change their rules. Sources:
- YouTube: [live encoder settings](https://support.google.com/youtube/answer/2853702)
- Twitch: [2K streaming announcement](https://blog.twitch.tv/en/2026/06/17/introducing-dual-format-and-2k-streaming-on-twitch/)
- Twitch: [broadcasting guidelines](https://help.twitch.tv/s/article/broadcasting-guidelines)
- Kick: [help center](https://help.kick.com/en/articles/7066931-how-to-stream-on-kick-com)

Standard uses 5000 kbps rather than Twitch's 4500 because YouTube's H.264 minimum for 1080p30 is 5 Mbps. Every encoder runs CBR with a 2-second keyframe interval and BT.709 colour. When Kick is on, both encoders use H.264 Main profile (Kick's guide); otherwise High.

Twitch's 1440p and 7500 kbps 1080p only work through Twitch's *Enhanced Broadcasting* protocol, which is on the [roadmap](#roadmap). Plain RTMP to Twitch tops out at 6000 kbps.

## Quality presets

| Preset | YouTube | Twitch | Kick |
|---|---|---|---|
| **Test** | 1080p60 · 6000 | 1080p60 · 6000 | 1080p60 · 6000 |
| **Standard** | 1080p30 · 5000 | 1080p30 · 5000 | 1080p30 · 5000 |
| **Max** | **1440p60 · 34 000** | 1080p60 · 6000 | 1080p60 · 6000 (8000 when Twitch is off) |
| **Custom** | your choice | your choice | your choice |

## Two encoders: how 1440p works

One encoder makes one stream, and every destination on it gets exactly the same video. Kick and Twitch can't take 1440p, so with **Max** the app runs **two hardware encoders from the same camera**:

- **High encoder:** YouTube at 1440p60, 34 000 kbps
- **Main encoder:** Twitch, Kick and custom servers at 1080p60. Its bitrate is the lowest maximum among them: 6000 when Twitch is on, 8000 for Kick alone.

The app asks the phone's encoder whether it can handle both before offering this. Most recent flagship phones can. On phones that can't, Max falls back to 1080p60 for everyone, and the Quality screen says so.

**Upload bandwidth adds up.** Every platform gets its own copy:

- YouTube 1440p + Twitch + Kick ≈ 34 + 6 + 6 ≈ **46 Mbps upload**.
- Three 1080p60 streams at 6000 ≈ **18.4 Mbps**.

The Live screen shows the total and warns you before you go live if it's high.

## Get the app

- **Download the APK:** open the repo's **Actions** tab → latest green "Android CI" run → **Artifacts → `app-debug-apk`**. Unzip it, copy `app-debug.apk` to your phone and open it. You'll need to allow installs from your browser or file manager.
- **Build it yourself:** install Android Studio (or the Android SDK + JDK 17+), then run:
  ```bash
  ./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
  ```

Requires Android 8.0 (API 26) or newer. The 1440p tier needs Android 10+ and a phone whose encoder supports it.

## Test without going live

[**docs/TESTING.md**](docs/TESTING.md) walks you through running a free local test server (MediaMTX) on Windows, Linux or macOS in about five minutes. You can watch all three test streams at 1080p60 on your computer and check that one failing destination doesn't affect the others, before you broadcast anything for real.

## How it's built

```
core/   Plain Kotlin (no Android). Platform limits, quality presets, the tier planner,
        the settings validator, bitrate-dial math and per-destination status logic.
        Fully unit-tested: ./gradlew -PcoreOnly :core:test (no Android SDK needed).
app/    Android app: Jetpack Compose UI, the streaming engine, encrypted settings.
```

- **Streaming:** [RootEncoder](https://github.com/pedroSG94/RootEncoder) (Apache-2.0). [`TieredMultiStream`](app/src/main/java/com/kelleyparker/multistream/stream/TieredMultiStream.kt) drives N RTMP clients per encoder. [`TierForwarder`](app/src/main/java/com/kelleyparker/multistream/stream/TierForwarder.kt) routes RootEncoder's second encoder to the second tier.
- **UI:** Compose + Material 3. Screens are stateless and screenshot-tested with Roborazzi (`./gradlew :app:recordRoborazziDebug`). CI uploads the screenshots as an artifact.

## Roadmap

**v1 (this release): multistream camera + mic.** YouTube, Twitch, Kick and custom RTMP(S); presets up to 1440p60; two-encoder tiers; setup wizard; per-platform status and retry.

**v2: Scenes & overlays (OBS-style built-in editor)**
- PNG, JPG, GIF and **PSD** overlays on top of the camera: frames, webcam borders, logos, "Starting soon" screens
- PSD import either flattened (like OBS) or **as separate layers**, so you can move and hide parts of a Photoshop design on the phone
- Drag, pinch-to-resize and rotate with snap guides; a layer panel with reorder, hide, lock and opacity
- Saved scenes you can switch **while live**
- Overlays are drawn once into the encoder pipeline, so every platform and both encoder tiers get them

**v3: Creator tools**
- Text, clock and countdown widgets
- **Unified chat** from YouTube, Twitch and Kick in one feed on screen, optionally shown in the stream as an overlay
- Follower/subscriber alerts
- "Starting soon", "BRB" and "Ending" scene templates

**v4: More video sources**
- Screen capture for mobile gaming
- USB/UVC webcams and HDMI capture dongles
- Picture-in-picture camera layer
- A second phone as a wireless camera

**v5: Quality & reliability**
- Twitch **Enhanced Broadcasting**: 1440p HEVC at 9000 kbps, 1080p at 7500, and Twitch's dual-format vertical stream
- HEVC/AV1 to YouTube (better picture per Mbps)
- Twitch ingest-region picker (nearest server list)
- Adaptive bitrate per destination when a connection struggles
- Stream health dashboard (dropped frames, latency, per-platform graphs)
- Record a local MP4 while streaming
- Keep streaming with the screen off (foreground service)
- SRT and WHIP output

**v6: Sign in with YouTube, Twitch and Kick (platform APIs)**
- Log in instead of pasting stream keys; the app fetches the key and ingest server for you
- Set the stream title, category/game and tags, and see viewer counts, from the app
- This is its own milestone because it needs:
  - a registered developer app on each platform (a Google Cloud project with YouTube Data API v3 live-streaming access, a Twitch Developer Console app, a Kick developer app)
  - OAuth 2.0 with PKCE so no secret ships inside the APK
  - possibly Google's OAuth verification review
  - handling each API's quotas
- Pasting a key will always remain as a fallback.

**v7: Audio**
- Mixer with background music, mic gain, noise suppression and music ducking

**Later**
- iOS version (Swift + HaishinKit)
- Tablet and landscape editor layouts
- Localization

## Contributing

Issues and pull requests are welcome. Before opening a PR:

```bash
./gradlew -PcoreOnly :core:test       # platform rules, no Android SDK needed
./gradlew lint testDebugUnitTest assembleDebug
```

## License

[Apache License 2.0](LICENSE). Streaming is powered by [RootEncoder](https://github.com/pedroSG94/RootEncoder) by pedroSG94, also Apache-2.0 (see [NOTICE](NOTICE)). YouTube, Twitch and Kick are trademarks of their owners; this project isn't affiliated with any of them.
