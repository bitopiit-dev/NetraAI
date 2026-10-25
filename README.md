# Netra AI — Garments Factory AI Surveillance (Android TV + Mobile)

Netra AI connects to your Dahua NVRs and IP cameras on the factory LAN (192.168.39.x),
shows them on a live video wall, and runs on-device AI to raise alerts. Video and AI never
leave your network.

## Ki ki ache (Features)

- **Live wall**: 1 / 4 / 9 / 16 grid, zone filter (Cutting, Sewing, Store, Gate…), auto "guard tour" paging, full-screen with HD main stream.
- **Live AI overlay** (full screen): person / vehicle boxes drawn on the video in real time.
- **AI patrol (background)**: every AI-enabled camera is checked in a round-robin loop using NVR snapshots. Garments rules:
  - *Restricted area* — any person in fabric store, accessories store, finished goods, server room
  - *After hours* — anyone on site outside the shift or on the weekly holiday (default Friday)
  - *Crowd gathering* — N+ people in one view
  - *Workstation unattended* — sewing line / QC table empty for X minutes during the shift
- **Dahua smart events** (from the NVR itself): tripwire, intrusion, tampering, video loss, object removed / left behind, HDD errors, SMD human/vehicle after hours.
- **Alerts** with annotated snapshot, push notification with picture, mark reviewed, 30-day retention.
- **Devices**: network scan of 192.168.39.1–254, bulk import of the 7 NVRs (all connected channels with their names come in automatically), per-camera AI / Restricted / Idle-watch switches.
- Works with **TV remote (D-pad)**: every focusable element gets a stitched "chalk-line" focus ring. Phone/tablet uses bottom navigation.
- Foreground service + auto-start after power cut / reboot. Passwords encrypted with Android Keystore.

## Build (Android Studio)

1. Android Studio **Koala (2024.1) or newer**, JDK 17 (bundled).
2. `File → Open` → this `NetraAI` folder. Let Gradle sync.
3. First build downloads the AI model (`efficientdet_lite0.tflite`, ~4 MB) into `app/src/main/assets/`.
   If the build PC has no internet, download it manually from
   `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite`
   and put it in `app/src/main/assets/`.
4. Run on an Android TV box / phone (Android 8.0+). For TV: enable *Developer options → USB/Network debugging*,
   then `adb connect <tv-ip>:5555`.
5. Release APK: `Build → Generate Signed App Bundle / APK` (replace the debug signing in `app/build.gradle.kts`).

## Dahua side prep (important)

1. **Create a separate user for Netra** on each NVR (e.g. `netra`) with *Live view* + *Playback snapshot* rights only. Don't use `admin`.
   Dahua locks an account after ~5 wrong passwords; Netra stops after one failed login to avoid that.
2. **RTSP on** (default port 554) and **HTTP/CGI on** (port 80). On newer firmware: *Network → Port → enable CGI*.
3. **Sub stream** of each camera: H.264, 704×576 (D1) or 640×480, 10–15 fps, 512 kbps. The wall uses the sub stream, full screen uses the main stream.
   H.265 main stream works only if the TV box has a hardware HEVC decoder (most 2020+ boxes do).
4. Optional but recommended: configure **IVS rules** (tripwire / intrusion) on gate, store and perimeter cameras in the NVR.
   Netra picks them up instantly through the event stream.

## First run

1. Devices → **Scan network** → `192.168.39.` → Scan.
2. Keep *Recorders only (recommended)*, enter the Netra username/password → **Add**.
   The 7 NVRs bring in their connected channels (110 cameras) with the names set on the NVR.
3. Camera channels tab → set **Zone** for each camera and switch on **Restricted** / **Idle watch** where needed.
4. Settings → shift hours, weekly holiday, crowd size.

## How it scales (honest numbers)

- One Android device cannot decode 110 live streams. The wall shows one page (up to 16 sub streams) at a time; TV boxes with weaker chips may manage only 9 — use the 9 grid there.
- AI patrol uses JPEG snapshots, not live decode. With 4 parallel workers a full sweep of 110 cameras takes about 30–60 s on a mid-range TV box. Raise *Parallel cameras* on faster hardware.
- Instant detection comes from the NVR's own smart events (7 connections total), which complements the patrol.
- For true real-time AI on all 110 cameras, run the same rules on a small server (Jetson / PC with GPU) and let this app be the viewer — the code is structured so `AiScheduler` can be swapped for a server feed.

## Video engine

Settings → Display → **Video engine**:
- **Auto** (default): Android's built-in player first; if a camera fails on both TCP and UDP it switches to VLC automatically.
- **Android player**: lightest, best for new Dahua firmware.
- **VLC**: for older / OEM Dahua firmware (anything desktop VLC plays). Uses a bit more CPU.

## Project layout

```
app/src/main/java/com/factory/netra/
  AppContainer.kt          manual DI
  dahua/                   DigestAuthenticator, DahuaClient (snapshot, probe, RTSP URL),
                           DahuaEventService (eventManager stream), NetworkScanner
  ai/                      ObjectDetectorEngine (MediaPipe), RuleEngine (garments rules), AiScheduler
  data/                    Room DB (devices, cameras, alerts), SettingsStore, CryptoBox, repositories
  service/                 SurveillanceService (foreground), AlertNotifier, BootReceiver
  ui/                      Jetpack Compose: Overview, Live wall, Full screen, Alerts, Devices, Settings
```

## Dahua API used

| Purpose | Endpoint |
|---|---|
| Live video | `rtsp://user:pass@ip:554/cam/realmonitor?channel=N&subtype=0/1` |
| Snapshot for AI | `/cgi-bin/snapshot.cgi?channel=N` |
| Device model | `/cgi-bin/magicBox.cgi?action=getDeviceType` |
| Channel names | `/cgi-bin/configManager.cgi?action=getConfig&name=ChannelTitle` |
| Connected channels | `/cgi-bin/LogicDeviceManager.cgi?action=getCameraState&uniqueChannels[0]=-1` |
| Smart events | `/cgi-bin/eventManager.cgi?action=attach&codes=[All]&heartbeat=5` |

## Next steps you can add

- Fire / smoke and PPE (mask, helmet in boiler/generator room) need a custom-trained TFLite model: drop it in `assets/` and add a rule in `RuleEngine`.
- Telegram / email escalation for HIGH alerts.
- Playback from NVR (`/cam/playback?channel=N&starttime=...`).
