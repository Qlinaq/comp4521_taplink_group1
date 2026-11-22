# TapLink

**Hong Kong Public Electronic Payment and Stored Value System-TapLink**

An English-language Android educational prototype for simulated stored-value cards, top-ups, merchant payments, spending insights and nearby service discovery. All balances and transactions are simulated; no bank, Octopus or FPS connection is used.

## Run

Open this folder in Android Studio, select **Gradle JDK 17 or 21**, sync, then run the `app` configuration on Android API 29+. The development verification environment uses JDK 21 and Pixel 6 / API 35. SDK Platform 35 is required for building.

```sh
# macOS / Linux
bash ./gradlew assembleDebug

# Windows
.\gradlew.bat assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`. The initial local database provides three demo cards and sample activity. User changes persist between launches. Core wallet workflows require no network or service credentials.

## Project structure

```text
app/src/main/java/com/taplink/app/
  domain/       Models, money rules, analytics and repository contract
  data/         Room database, seed catalogue and transactional repository
  ui/           Compose screens, retained state and custom spending chart
  platform/     Android notifications, workers, widget, location and sensors
docs/           Course requirements, demo guide, AI acknowledgement and handoff
tools/          Local verification and clean handoff packaging
```

See [API_CONTRACT.md](API_CONTRACT.md) before changing persistence or transaction behavior. Monetary values use integer cents. Related balance and ledger changes share a Room transaction. Repeated confirmation attempts use a stable operation identifier to prevent duplicate money movements.

## Verification

```sh
bash ./gradlew testDebugUnitTest lintDebug assembleDebug
# With a connected emulator/device:
bash ./gradlew connectedDebugAndroidTest
```

Windows users may run `tools/verify.ps1` (optionally `-DeviceTests`). GitHub Actions builds, runs unit tests and lint, and uploads the debug APK and reports. A workflow definition alone is not a passing result: consult the latest run and the recorded local verification.

## Team handoff

- [Setup and contribution guide](docs/TEAM_HANDOFF.md)
- [Demonstration script](docs/DEMO_SCRIPT.md)
- [Course delivery requirements](docs/COURSE_REQUIREMENTS.md)
- [Implementation decisions](docs/DEVELOPMENT_DECISIONS.md)
- [Generative AI acknowledgement](docs/AI_USE.md)
- [References and provenance](docs/REFERENCES.md)

Fill in the actual group number and remaining member details in `TEAM.json` before submission. The official final delivery requires a report (maximum 5,000 words), a program ZIP and a separate presentation video (maximum 20 minutes). This repository is the team's editable implementation and handoff workspace.

To create a portable source/APK archive after a successful build, run `python tools/package.py`. It excludes machine-specific configuration, caches, credentials and generated build directories.
