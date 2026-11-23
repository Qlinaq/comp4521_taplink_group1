# Team handoff

## Open and run

1. Clone the private repository or extract the source folder from the handoff ZIP.
2. Open the `TapLink` folder (the one containing `settings.gradle.kts`) in Android Studio.
3. Set the project's Gradle JDK to **17 or 21**. JDK 21 is used for verification. A separately installed system JDK 27 should not be selected for this Gradle version.
4. Install Android SDK Platform 35 if prompted. Let Gradle sync complete.
5. Select an Android device/emulator with API 29 or newer and run `app`.

The handoff APK can also be installed directly. All wallet data is local to that installation. No backend credentials or payment accounts are needed. A permission denial should not block core wallet use.

Start with [the demo script](DEMO_SCRIPT.md), [verified results](VERIFICATION.md), [feature boundaries](FEATURE_STATUS.md), [the account/language design record](AUTH_DESIGN.md) and [the accessibility/support record](ACCESSIBILITY.md). The source ZIP includes these documents, the report draft and screenshots. The supplied APK is a debug demonstration build; a teammate building with a different debug signing key may need to uninstall the existing demo installation first, which removes its local data.

## Code map

| Area | Location | Responsibilities |
| --- | --- | --- |
| Public model/API | `API_CONTRACT.md`, `domain/` | Integer money, validation, analytics, repository contract |
| Persistence | `data/` | Room tables, initialization, atomic operations and local state |
| Accounts | `domain/auth/`, `data/auth/`, `ui/auth/` | Identifier rules, PBKDF2 hashing, OTP and lockout policy; account/code tables, session store; welcome, sign-in/up, verification, reset and the Settings account section |
| Language | `domain/AppLanguage.kt`, `ui/Localization.kt`, `ui/Labels.kt`, `res/values-zh-rTW`, `res/values-zh-rCN` | Locale switch, localised dates, enum labels and the resource parity guard |
| Accessibility | `ui/settings/AccessibilityScreen.kt`, `ui/theme/Theme.kt`, `platform/VoiceReader.kt` | Large text, high contrast, read-aloud and the simplified home flow |
| Notifications and contact | `ui/settings/NotificationSettingsScreen.kt`, `ui/settings/ContactScreen.kt`, `platform/MessageCopy.kt`, `platform/ExternalIntents.kt` | Category switches, permission status, localised inbox rows, WhatsApp/dialer/e-mail intents |
| AI support | `data/support/`, `ui/support/`, `domain/SupportContacts.kt` | Quota, prompt rules, offline FAQ fallback, the DeepSeek client, chat and sign-language screens |
| App experience | `ui/`, `MainActivity.kt` | Compose screens, navigation, retained form state and feedback |
| Android features | `platform/` | Notifications, background work, widget, sensors and location helpers |
| Verification | `app/src/test/`, `app/src/androidTest/`, `tools/verify.ps1` | Unit and emulator checks |
| Course delivery | `docs/`, `TEAM.json` | Requirements, demonstration, AI acknowledgement and team metadata |

## Making a change

Create a branch for a concrete improvement, for example `ui/home-polish` or `fix/history-filter`. Keep money mutations in the repository. Give your AI tool the repository contract and the specific expected behavior, then inspect the diff and run the relevant checks. Avoid replacing a working subsystem simply to change its appearance.

On Windows, `powershell -ExecutionPolicy Bypass -File tools/verify.ps1` runs build, unit tests and lint. Add `-DeviceTests` with a ready emulator for instrumentation tests. On other systems run `bash ./gradlew testDebugUnitTest lintDebug assembleDebug` and optionally `connectedDebugAndroidTest`.

If the Gradle device-test infrastructure has not been downloaded, the same instrumentation suite can run directly after `assembleDebugAndroidTest`: install both generated APKs with `adb install -r`, then run `adb shell am instrument -w -r com.taplink.app.test/androidx.test.runner.AndroidJUnitRunner`. The recorded local runs used `connectedDebugAndroidTest` (49 tests) and this direct route (29 tests in the earlier round).

Three conventions must be respected by any change:
- **Every user-visible string lives in all three `strings.xml` files.** `LocalizationParityTest` fails the build otherwise. Use `stringResource`, `Enum.label()`, and `localizedMessage` rather than literals.
- **Never log, persist or place a password in saved state.** Credentials go through `PasswordHasher`; verification codes go through `CodeHasher`; the simulated inbox keeps the last delivery in memory only.
- **Never put the API key in the repository.** `DEEPSEEK_API_KEY` lives in git-ignored `local.properties` and reaches the app through `BuildConfig`. The key is therefore inside any APK you build: treat every build as public, never commit `local.properties`, and never paste the key into a document, screenshot or chat. A release build must proxy the call through a server.

Use a pull request to describe the trigger, resulting behavior and verification. Keep `main` demonstrable. No global Java, BIOS or virtualization changes are part of the project setup.

## Final group actions

- Fill in `TEAM.json` with the confirmed group number, all names/SIDs and actual contributions.
- Review and adapt the final report; keep it under 5,000 words and use the supplied course cover page.
- Record the presentation video (maximum 20 minutes).
- Update the GenAI acknowledgement to match actual use by all members.
- Rename the final report and program ZIP using the actual assigned group number and submit once per group.

Teammate repository access can be added once their GitHub usernames are known. The repository starts private; sharing a URL alone does not grant access.
