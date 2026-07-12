# Neon Racer — Turbo Edition

A 3D car driving game built with **Three.js**, wrapped in a **Kotlin WebView** Android app, with a fully automated **GitHub Actions** CI/CD pipeline that builds and publishes signed release APKs.

---

## Project Structure

```
games/
├── android/                         # Android Studio project
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── assets/www/          # Three.js game (loaded by WebView)
│   │   │   │   ├── index.html
│   │   │   │   ├── css/style.css
│   │   │   │   └── js/
│   │   │   │       ├── settings.js  # localStorage settings
│   │   │   │       ├── controls.js  # touch / tilt / keyboard input
│   │   │   │       ├── car.js       # car physics + 3D model
│   │   │   │       ├── tracks.js    # 3 track builders
│   │   │   │       ├── ai.js        # AI opponents
│   │   │   │       ├── audio.js     # Web Audio engine sounds
│   │   │   │       ├── ui.js        # all menus, HUD, overlays
│   │   │   │       └── game.js      # main game loop & state machine
│   │   │   ├── java/com/neonracers/
│   │   │   │   ├── MainActivity.kt  # WebView host
│   │   │   │   └── SplashActivity.kt
│   │   │   ├── res/                 # Android resources
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   ├── build.gradle
│   ├── settings.gradle
│   └── gradlew
└── .github/workflows/
    ├── release.yml                  # signed APK on version tag
    └── debug-build.yml              # debug artifact on main push
```

---

## Game Features

| Feature | Detail |
|---|---|
| **Physics** | Speed/friction/drift model, suspension bounce |
| **Tracks** | Neon City · Desert Storm · Coastal Circuit |
| **Race Mode** | 3-lap circuit, AI opponents, countdown, results screen |
| **Free Mode** | Open drive, no objectives |
| **Controls** | Touch buttons, steering wheel, device tilt, keyboard |
| **Camera** | Chase · Cockpit · Hood (tap 📷 to cycle) |
| **HUD** | Speedometer, lap timer, best lap, race position, minimap, gear, drift indicator |
| **Audio** | Procedural engine sound, chiptune music, checkpoint/lap/finish SFX |
| **Settings** | Graphics quality, volumes, control scheme, camera — all persisted to localStorage |

---

## Local Development

### Play in browser

Three.js must be present locally. Download it once:

```bash
curl -fsSL https://cdnjs.cloudflare.com/ajax/libs/three.js/r160/three.min.js \
  -o android/app/src/main/assets/www/js/three.min.js
```

Then open `android/app/src/main/assets/www/index.html` in a browser:

```bash
# Python simple server (avoids file:// CORS issues in some browsers)
cd android/app/src/main/assets/www
python3 -m http.server 8080
# open http://localhost:8080
```

### Build debug APK locally

Requirements: JDK 17, Android SDK with build tools 35.

```bash
cd android
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

Install on connected device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Generating a Release Keystore

Run this once on your local machine and keep the keystore file safe:

```bash
keytool -genkeypair \
  -alias neonracer \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore release.keystore \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=Neon Racer, OU=Games, O=YourOrg, L=City, ST=State, C=US"
```

Encode it as base64:

```bash
base64 -i release.keystore | tr -d '\n' > release.keystore.b64
```

---

## Setting Up GitHub Secrets

In your repository go to **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Contents of `release.keystore.b64` |
| `KEYSTORE_PASSWORD` | Your `-storepass` value |
| `KEY_ALIAS` | `neonracer` (or whatever alias you chose) |
| `KEY_PASSWORD` | Your `-keypass` value |

---

## Publishing a Release

Push a version tag to trigger the release pipeline:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow will:
1. Download Three.js r160 into the assets folder
2. Decode the keystore from GitHub Secrets
3. Build a signed release APK with `./gradlew assembleRelease`
4. Create a GitHub Release at the tag with the APK attached

The debug workflow runs on every push to `main` and uploads the debug APK as a workflow artifact (14-day retention).

---

## Android WebView Details

- Loads from `file:///android_asset/www/index.html` — **100% offline**
- `localStorage` enabled for settings persistence
- Hardware-accelerated WebView (`LAYER_TYPE_HARDWARE`)
- Landscape-locked, fullscreen immersive mode
- Back button: pause in-game, exit confirm on main menu
- JS bridge: `window.NeonRacer.exit()` calls `finish()` on the Activity

---

## Controls Reference

| Input | Action |
|---|---|
| ▲ / W / ↑ | Throttle |
| ▼ / S / ↓ | Brake / Reverse |
| ◄ ► / A D / ← → | Steer |
| E-BRAKE / Space | Handbrake (drift) |
| ⏸ / P / Esc | Pause |
| 📷 / C | Cycle camera view |

---

## Customization

- **Add a track**: Add an entry to `Tracks.TRACK_LIST` and implement a `buildMyTrack()` function in `tracks.js` following the same pattern (returns `{ name, checkpoints, waypoints, startPosition, startHeading, lapCount, getTerrainY }`).
- **Change car color**: Pass a different hex color to `Car.buildMesh()` in `game.js`.
- **Tune physics**: Adjust constants at the top of `car.js` (MAX_SPEED_KMH, ACCELERATION, DRIFT_FACTOR, etc.).
- **Package name**: Change `com.neonracers` in `AndroidManifest.xml`, `build.gradle`, and the Kotlin source files.
