# Test multistreaming locally in 5 minutes (MediaMTX)

Before going live for real, you can check everything on your own computer:

- the phone streams to **three destinations at once**
- each one arrives at **1080p60**
- if one destination fails, **the others keep going**

You'll use **[MediaMTX](https://github.com/bluenviron/mediamtx)**, a free, single-file media server. Out of the box it accepts RTMP streams on port **1935** on any path, with no password, and lets you watch them in VLC or a browser. Nothing is broadcast publicly.

```
 phone ──► rtmp://<your-PC>:1935/live/youtube ─┐
       ──► rtmp://<your-PC>:1935/live/twitch  ─┼─► MediaMTX ─► VLC / browser
       ──► rtmp://<your-PC>:1935/live/kick    ─┘
```

> Verified with MediaMTX **v1.21.2**. The steps below were run end to end: three simultaneous 1080p60 H.264 + AAC streams published to `live/youtube`, `live/twitch` and `live/kick` all read back at 1920×1080, 60 fps. Newer versions work the same way; swap the version number in the file names.

---

## 1. Start MediaMTX on your computer

### Windows

1. Download **`mediamtx_v1.21.2_windows_amd64.zip`** from the [releases page](https://github.com/bluenviron/mediamtx/releases).
2. Unzip it, open the folder, and double-click **`mediamtx.exe`**. Or, in PowerShell in that folder:
   ```powershell
   .\mediamtx.exe
   ```
   Leave the window open. You should see `[RTMP] started with listener on :1935`.
3. When Windows Defender Firewall asks, allow access on **Private networks**. If you missed the prompt, run this once in an **administrator** PowerShell:
   ```powershell
   New-NetFirewallRule -DisplayName "MediaMTX" -Direction Inbound -Protocol TCP -LocalPort 1935,8888,8889 -Action Allow
   ```
4. Find your PC's address:
   ```powershell
   ipconfig
   ```
   Use the **IPv4 Address** of your Wi-Fi or Ethernet adapter, e.g. `192.168.1.20`.

### Linux

1. Download and start it (use `linux_arm64` on a Raspberry Pi or other ARM board):
   ```bash
   curl -LO https://github.com/bluenviron/mediamtx/releases/download/v1.21.2/mediamtx_v1.21.2_linux_amd64.tar.gz
   tar xzf mediamtx_v1.21.2_linux_amd64.tar.gz
   ./mediamtx
   ```
   **Or with Docker:**
   ```bash
   docker run --rm -it --network=host bluenviron/mediamtx:latest
   ```
2. If a firewall is on, open the ports:
   ```bash
   sudo ufw allow 1935,8888,8889/tcp
   ```
3. Find your address:
   ```bash
   hostname -I
   ```

### macOS

1. Download **`mediamtx_v1.21.2_darwin_arm64.tar.gz`** (Apple Silicon) or **`darwin_amd64`** (Intel) from the [releases page](https://github.com/bluenviron/mediamtx/releases).
2. In Terminal, in your Downloads folder:
   ```bash
   tar xzf mediamtx_v1.21.2_darwin_arm64.tar.gz
   xattr -d com.apple.quarantine mediamtx   # lets macOS run a downloaded binary
   ./mediamtx
   ```
3. Click **Allow** if macOS asks about incoming connections.
4. Find your address:
   ```bash
   ipconfig getifaddr en0
   ```

---

## 2. Point the app at it

Put the phone on the **same Wi-Fi** as your computer, then:

1. In the setup wizard (or **Platforms → Add platform**), choose **Custom server**.
   - **Server address:** `rtmp://192.168.1.20:1935/live/youtube` (use your computer's address)
   - **Stream key:** leave empty
   - Tap **Check connection**. It should say *Server reachable*.
2. Add two more **Custom server** destinations ending in **`/live/twitch`** and **`/live/kick`**. You now have three simulated platforms, labelled *Custom: youtube*, *Custom: twitch* and *Custom: kick*.
3. Open **Quality** and pick **Test** (1080p60 at 6000 kbps to every destination).
4. Back on the camera screen, tap **Go Live**.

The MediaMTX window shows three lines like:
```
[RTMP] [conn 192.168.1.50:41234] is publishing to path 'live/youtube'
```

## 3. Watch the streams

- **VLC:** *Media → Open Network Stream* → `rtmp://localhost:1935/live/youtube`. Open a second and third VLC window for `live/twitch` and `live/kick`. *Tools → Codec Information* should show **1920×1080** and **60** frames per second.
- **Browser:** `http://localhost:8889/live/kick/` (WebRTC, about half a second of delay) or `http://localhost:8888/live/kick/` (HLS, a few seconds of delay). From another device, use your computer's address instead of `localhost`.

## 4. Check that one failure doesn't stop the others

1. While live, tap **Stop** on the *Custom: kick* card only. `youtube` and `twitch` keep playing in VLC.
2. Tap **Reconnect** on *Custom: kick*. It comes back within a few seconds.
3. For a hard failure: stop streaming, open **Platforms → Custom: kick**, change the port to `1936` (nothing listens there) and go live again. It shows **Reconnecting…** with growing waits between tries (and eventually **Failed** with a plain-language reason), while the other two stream normally.

## 5. Optional: two-encoder test (1440p + 1080p)

On phones that support it, the **Max** preset sends YouTube 1440p60 from a second encoder:

1. Add a **YouTube** destination, choose **Other server…** and enter `rtmp://192.168.1.20:1935/live` with stream key `youtube`. Turn off *Custom: youtube* and keep *Custom: twitch* and *Custom: kick*.
2. Pick **Max** and go live.
3. `live/youtube` should report **2560×1440**, and `live/twitch` and `live/kick` **1920×1080**, all at 60 fps.

If the Quality screen says *"This phone can't run two encoders at once"*, your phone uses a single 1080p60 stream instead. That's expected.

## Troubleshooting

| Symptom | Fix |
|---|---|
| *Check connection* fails | Phone and computer must be on the same network. Guest Wi-Fi and some mesh "isolation" modes block device-to-device traffic. Disconnect VPNs on both. |
| MediaMTX shows nothing | Re-check the address and port `1935`, and the firewall rule above. |
| Video stutters on the computer | Three 1080p60 streams need about **18.4 Mbps** of Wi-Fi upload from the phone. Try **Standard**, or move closer to the router. |
| `address already in use` when starting MediaMTX | Another server (OBS, another MediaMTX) is using port 1935. Close it. |

## What this does *not* test

MediaMTX accepts anything, so it doesn't enforce Twitch, Kick or YouTube's limits. The app's Quality screen already blocks settings a platform would reject. For a final check, do a short real stream:

- YouTube: set to **Unlisted**
- Twitch: turn on **Test mode** in the Twitch platform settings (Twitch's bandwidth test; never public)
- Kick: a short stream

Then look at each platform's stream-health panel.
