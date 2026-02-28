# Privacy Shield (PocketNarc)

A native Android application that assists users in screening for potential hidden cameras, microphones, and tracking devices using phone sensors.

## ⚠️ Important Disclaimer

**This application uses phone sensors to assist in screening for potential surveillance devices. It cannot detect all threats, especially passive or unpowered ones. For critical situations, consult professional equipment and services.**

This is an assistive screening tool only—not a guaranteed professional-grade detector.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Min SDK:** 26
- **Target SDK:** 35

## Project Structure

```
app/src/main/java/com/pocketnarc/privacyshield/
├── data/                    # Data layer
│   └── OnboardingRepository.kt
├── ui/
│   ├── navigation/          # NavHost, routes
│   ├── screens/
│   │   ├── onboarding/      # Mandatory disclaimer + acknowledgment
│   │   ├── home/            # Scan option grid
│   │   ├── magnetometer/    # EM Field Scanner (placeholder)
│   │   ├── lens/            # Lens Reflection Detector (placeholder)
│   │   ├── network/         # Wi-Fi/Bluetooth Scanner (placeholder)
│   │   └── audio/           # Audio Environment Monitor (placeholder)
│   └── theme/               # Material 3 theme
```

## Features (Current)

- [x] Mandatory onboarding with disclaimer and acknowledgment checkbox
- [x] Home screen with scan option cards
- [x] Navigation to all feature screens
- [ ] Magnetometer-based EM field scanner
- [ ] Lens reflection detector (CameraX + flashlight)
- [ ] Network scanner (Wi-Fi/Bluetooth)
- [ ] Audio environment monitor

## Building

```bash
./gradlew assembleDebug
```

**Requirements:**
- Android SDK (API 35)
- Java 17 or 21 (recommended). Java 25+ may cause Kotlin compiler issues—set `JAVA_HOME` to a compatible JDK if needed.
- Android Studio (or command-line tools)

## Launcher Icons

Replace `ic_launcher_foreground.xml` and add proper mipmap assets for production. Consider using [Android Asset Studio](https://romannurik.github.io/AndroidAssetStudio/) for icon generation.

## Roadmap

See the project overview document for the full 8–12 week development plan.
