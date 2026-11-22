# Team handoff

## Open and run

1. Clone the private repository or extract the source folder from the handoff ZIP.
2. Open the `TapLink` folder (the one containing `settings.gradle.kts`) in Android Studio.
3. Set the project's Gradle JDK to **17 or 21**. JDK 21 is used for verification. A separately installed system JDK 27 should not be selected for this Gradle version.
4. Install Android SDK Platform 35 if prompted. Let Gradle sync complete.
5. Select an Android device/emulator with API 29 or newer and run `app`.

The handoff APK can also be installed directly. All wallet data is local to that installation. No backend credentials or payment accounts are needed. A permission denial should not block core wallet use.

## Code map

| Area | Location | Responsibilities |
| --- | --- | --- |
| Public model/API | `API_CONTRACT.md`, `domain/` | Integer money, validation, analytics, repository contract |
| Persistence | `data/` | Room tables, initialization, atomic operations and local state |
| App experience | `ui/`, `MainActivity.kt` | Compose screens, navigation, retained form state and feedback |
| Android features | `platform/` | Notifications, background work, widget, sensors and location helpers |
| Verification | `app/src/test/`, `app/src/androidTest/`, `tools/verify.ps1` | Unit and emulator checks |
| Course delivery | `docs/`, `TEAM.json` | Requirements, demonstration, AI acknowledgement and team metadata |

## Making a change

Create a branch for a concrete improvement, for example `ui/home-polish` or `fix/history-filter`. Keep money mutations in the repository. Give your AI tool the repository contract and the specific expected behavior, then inspect the diff and run the relevant checks. Avoid replacing a working subsystem simply to change its appearance.

On Windows, `powershell -ExecutionPolicy Bypass -File tools/verify.ps1` runs build, unit tests and lint. Add `-DeviceTests` with a ready emulator for instrumentation tests. On other systems run `bash ./gradlew testDebugUnitTest lintDebug assembleDebug` and optionally `connectedDebugAndroidTest`.

Use a pull request to describe the trigger, resulting behavior and verification. Keep `main` demonstrable. No global Java, BIOS or virtualization changes are part of the project setup.

## Final group actions

- Fill in `TEAM.json` with the confirmed group number, all names/SIDs and actual contributions.
- Review and adapt the final report; keep it under 5,000 words and use the supplied course cover page.
- Record the presentation video (maximum 20 minutes).
- Update the GenAI acknowledgement to match actual use by all members.
- Rename the final report and program ZIP using the actual assigned group number and submit once per group.

Teammate repository access can be added once their GitHub usernames are known. The repository starts private; sharing a URL alone does not grant access.
