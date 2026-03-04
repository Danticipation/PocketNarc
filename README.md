# Privacy Shield (PrivatAid)

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
│   │   ├── magnetometer/    # Magnetic Anomaly Scanner (Active)
│   │   ├── lens/            # Lens Reflection Sweep (Active)
│   │   ├── network/         # Network Device Audit (Active)
│   │   ├── audio/           # Acoustic Probe / Ultrasound (Active)
│   │   └── bluetooth/       # BLE Tracker Hunt (Active)
│   └── theme/               # Material 3 theme
```

## Features (Current)

- [x] Mandatory onboarding with disclaimer and acknowledgment checkbox
- [x] Home screen with forensic scan option cards
- [x] Magnetic Field Detector (EMF Anomaly Scanner)
- [x] Lens Reflection Scanner (CameraX-based optical glint detection)
- [x] Network Device Discovery (Local subnet audit for cameras)
- [x] Bluetooth & AirTag Tracker (BLE Beacon analysis)
- [x] Ultrasonic & Audio Analyzer (High-frequency environment monitor)
- [ ] Device Security Audit (Kernel & Play Integrity - In Progress)

## Building

```bash
./gradlew assembleDebug
```

**Requirements:**
- Android SDK (API 35)
- Java 17 or 21 (recommended). Java 25+ may cause Kotlin compiler issues—set `JAVA_HOME` to a compatible JDK if needed.
- Android Studio (or command-line tools)

## Roadmap

See the project overview document for the full 8–12 week development plan.
