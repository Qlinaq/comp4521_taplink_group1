# TapLink

**Hong Kong Public Electronic Payment and Stored Value System-TapLink**

An Android educational prototype for simulated stored-value cards, top-ups, merchant payments, spending insights and nearby service discovery, in English, Traditional Chinese and Simplified Chinese. Accounts (sign-up, sign-in by email or Hong Kong phone number, password reset, session gate) run locally with hashed credentials and a simulated code inbox. A public-service round adds notification settings with per-category switches, a contact screen (WhatsApp 2520 1314, hotline, AI chat, sign-language chapters), an accessibility and elderly-friendly mode (150% text, high contrast, read-aloud, simplified flow) and swipeable home cards. All balances and transactions are simulated; no bank, Octopus or FPS connection is used.

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
  domain/       Models, money rules, analytics, auth rules/crypto and repository contracts
  domain/auth/  Identifier rules, password hashing, OTP and lockout policies
  data/         Room database, seed catalogue and transactional repository
  data/auth/    Account and verification-code tables, Room auth repository, session store
  ui/           Compose screens, retained state, custom spending chart, localisation
  ui/auth/      Welcome, sign-in/up, verification, reset and the Settings account section
  ui/settings/  Notification, accessibility and contact screens
  ui/support/   AI text support chat and the sign-language chapters
  data/support/ Quota, prompt rules, offline FAQ matcher and the DeepSeek client
  platform/     Android notifications, workers, widget, location, sensors, speech
docs/           Course requirements, demo guide, AI acknowledgement and handoff
tools/          Local verification and clean handoff packaging
```

See [API_CONTRACT.md](API_CONTRACT.md) before changing persistence or transaction behavior. Monetary values use integer cents. Related balance and ledger changes share a Room transaction. Repeated confirmation attempts use a stable operation identifier to prevent duplicate money movements.

## Verification

Recorded local result: **94 unit tests + 49 Android instrumentation tests passed; lint: 0 errors (51 advisory warnings).** See the [verification record](docs/VERIFICATION.md) for all three verification rounds, the tested environment and the stated limits.

```sh
bash ./gradlew testDebugUnitTest lintDebug assembleDebug
# With a connected emulator/device:
bash ./gradlew connectedDebugAndroidTest
```

Windows users may run `tools/verify.ps1` (optionally `-DeviceTests`). GitHub Actions builds, runs unit tests and lint, and uploads the debug APK and reports. A workflow definition alone is not a passing result: consult the latest run and the recorded local verification.

The workflow retries once, and only once, when a run fails while *resolving a dependency* (`could not resolve`, a 5xx from a repository, a timeout) rather than on code. The first run of the workflow tripped over exactly this: the KSP plugin artifact could not be fetched although the same commit built on a re-run. A compile, test or lint failure does not match the retry pattern, so a red run still means the code is red.

## Team handoff

- [Setup and contribution guide](docs/TEAM_HANDOFF.md)
- [Proposal feature map and implementation limits](docs/FEATURE_STATUS.md)
- [Accounts, sessions and interface language: design and limits](docs/AUTH_DESIGN.md)
- [Accessibility, notifications and AI support: what is claimed and what is not](docs/ACCESSIBILITY.md)
- [Screenshots of the running app](docs/SCREENSHOTS.md)
- [Demonstration script](docs/DEMO_SCRIPT.md)
- [Final report draft for team review](docs/FINAL_REPORT_DRAFT.md)
- [Course delivery requirements](docs/COURSE_REQUIREMENTS.md)
- [Implementation decisions](docs/DEVELOPMENT_DECISIONS.md)
- [Generative AI acknowledgement](docs/AI_USE.md)
- [References and provenance](docs/REFERENCES.md)

Fill in the actual group number and remaining member details in `TEAM.json` before submission. The official final delivery requires a report (maximum 5,000 words), a program ZIP and a separate presentation video (maximum 20 minutes). This repository is the team's editable implementation and handoff workspace.

To create a portable source/APK archive after a successful build, run `python tools/package.py`. It excludes machine-specific configuration, caches, credentials and generated build directories.
