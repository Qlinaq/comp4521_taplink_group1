# Verification record

Round 1 verified on 22 November 2026 (Hong Kong time), implementation revision `97b06db` (wallet F01–F18). Round 2 verified on 23 November 2026, working tree on top of `1858cff` (accounts F19 and interface language F20). Round 3 verified on 23 November 2026, working tree on top of round 2 (notifications and accessibility F21–F25). Round 4 verified on 23 November 2026, working tree on top of round 3 (per-account wallets F26, account entry F27 and the Chinese-input diagnosis). Round 5 verified on 23 November 2026, working tree on top of round 4 (page-heading system and chart typography F28).

**Round 5 result: debug APK built successfully; 94 JVM tests passed; 50 Android instrumentation tests passed; Android lint reported zero errors and 51 warnings.**

**Round 4 result: debug APK built successfully; 94 JVM tests passed; 50 Android instrumentation tests passed; Android lint reported zero errors and 51 warnings.**

**Round 3 result: debug APK built successfully; 94 JVM tests passed; 49 Android instrumentation tests passed; Android lint reported zero errors and 51 warnings.**

**Round 2 result: debug APK built successfully; 84 JVM tests passed; 49 Android instrumentation tests passed; Android lint reported zero errors and 56 warnings.** Round 1 recorded 51 JVM tests, 29 instrumentation tests and zero warnings; the warning count grew because the two new features add deprecation and unused-resource notices, not because earlier checks regressed.

**Round 1 result: debug APK built successfully; 51 JVM tests passed; 29 Android instrumentation tests passed; Android lint reported zero errors and zero warnings.** The installed wallet workflow was also checked against its persisted ledger. These results apply to the scope below, not every possible device or lifecycle interleaving.

## Build and environment

- Windows development host; JDK 21.0.13, Gradle 8.10.2, AGP 8.7.2, Kotlin 2.0.21.
- Compile/target API 35; minimum API 29. Runtime checks used the existing Pixel 6 emulator, Android API 35.
- Final local tasks: `assembleDebug`, `testDebugUnitTest`, `lintDebug`; the instrumentation APK was also assembled.
- APK SHA-256: `da6f752ae1ce042d2444e3d161a6ee8f152e018d828e621198be8600bee94a20`.
- [Final build output](verification/build.txt), [unit suite results](verification/unit-results.json), [lint XML](verification/lint.xml), [instrumentation output](verification/instrumentation.txt).

Independent Linux CI also passed the build, unit tests and lint using the committed default repositories: [successful baseline run](https://github.com/zwucc-HKUST/comp4521-taplink/actions/runs/45029892149), revision `ce690f2`. That run precedes the small system-bar contrast fix; the final local evidence above includes the fix. Later pushes are checked by the same workflow; consult Actions for the newest result.

The workstation intermittently failed TLS downloads from Google/Maven. A temporary, uncommitted Gradle init script routed a small subset of test dependencies through an HTTPS Maven mirror; one cached Android dependency was downloaded with a matching checksum. Versions and the committed Google/Maven Central repository configuration were unchanged. Final local compilation ran from the populated cache. No global JDK, proxy, BIOS, or virtualization setting was changed.

Instrumentation ran using the installed test APK and `adb shell am instrument -w -r com.taplink.app.test/androidx.test.runner.AndroidJUnitRunner`. All 29 tests passed again after the final theme change. The separate Gradle `connectedDebugAndroidTest` invocation could not resolve an uncached UTP helper in offline mode; it is not claimed as a successful Gradle task.

## Automated coverage

| Suite | Passed | Main coverage |
| --- | ---: | --- |
| MoneyTest | 3 | Exact cents, invalid/ambiguous amounts, overflow and formatting |
| WalletRulesTest | 14 | Frozen cards, affordability, reload/alert boundaries, cap, randomized conservation |
| WalletAnalyticsTest | 6 | Combined filters, Hong Kong month bounds, debit-only totals, subsidy/pass rules, receipts |
| PlatformPoliciesTest | 9 | Reminder/notification policy, permission fallback, privacy-related presentation |
| ShakeDetectorTest | 5 | Peaks, timing/debounce, reset and invalid sensor samples |
| WalletViewModelTest | 14 | Delayed observations, stale receipt reads, cancellation recovery, dialog completion, stale location callbacks |
| RoomTapLinkRepositoryTest | 25 | Real Room transactions, per-owner isolation, concurrent/idempotent operations, injected write failures, cancellation, stable ledger order, preference transforms, offers/passes/subsidies and reopen |
| PlatformAndroidTest | 5 | Denied location, map/share intent construction/fallback, RemoteViews inflation and removal of a previously exposed balance |

The 24 Room cases use dedicated test databases. ViewModel delayed-result tests execute the state model; they do not independently certify Android Activity recreation during every suspended operation. Intent tests verify the constructed intent/chooser behavior using controlled contexts, not successful delivery to another person or app.

## Installed-app checks

| Check | Observed result |
| --- | --- |
| First launch | Three demo cards and seeded activity appeared; Campus balance HK$428.50. |
| Card creation and selection | Created a zero-balance card named Demo and explicitly selected it. |
| Blank amount | Top-up review was rejected with a clear validation message. |
| Top-up | Confirmed HK$100; receipt and balance both showed HK$100. |
| Reload preferences | Enabled auto reload, threshold HK$80, amount HK$50; saved values remained visible. |
| Rotation | Payment form retained its card, Campus Cafe merchant, and HK$30.00 when rotated to landscape and back. |
| Payment and reload | Confirmed HK$30; receipt showed a HK$50 reload and HK$120 final balance. |
| Spending/filtering | Filtering to Demo showed HK$30 total, entirely Food; credits were excluded. |
| Empty search | A non-matching query produced zero transactions and an explicit empty state; clearing it restored results. |
| Location denial | Denied the actual Android approximate-location prompt; manual district browsing and the demo map remained usable. |
| Insufficient payment | HK$999 was rejected before confirmation; balance stayed HK$120. |
| Frozen card | Confirmed freeze; top-up was rejected with an explanation; unfreeze restored normal controls. |
| Rename and theme | Renamed Demo to Showcase, selected Dark, and verified the dashboard. Dark system-bar icons initially lacked contrast; the final patch makes them light and readable. |
| Restart and persistence | Force-stop/relaunch retained Showcase, HK$120, and the selected dark theme. |
| Ledger consistency | Read-only snapshot contained exactly the three intended money entries; no rejected operation added an entry. SQLite integrity check returned `ok`. |

The final Showcase ledger is recorded in [manual-wallet.json](verification/manual-wallet.json):

| Order | Type | Amount | Balance after |
| --- | --- | ---: | ---: |
| 1 | Top-up | +HK$100.00 | HK$100.00 |
| 2 | Payment | −HK$30.00 | HK$70.00 |
| 3 | Automatic reload | +HK$50.00 | HK$120.00 |

All these money changes were made through the app UI. The database copy was inspected read-only after force-stop; it was not used to inject successful demo outcomes. The emulator retains this Showcase card. A fresh installation of the delivered APK initializes the original three seed cards.


## Round 2: accounts and interface language (23 November 2026)

Verified on a macOS host with JDK 21, Gradle 8.10.2, AGP 8.7.2 and the `Small_Phone` AVD running API 35 (720×1280, density 320). The database moved to version 3 through two additive migrations; the version 1 and 2 schemas remain exported under `app/schemas/`.

### Automated coverage added

| Suite | Passed | Main coverage |
| --- | ---: | --- |
| `AuthRulesTest` | 17 | Email/phone normalisation, masking, password truth table, OTP lifetime/attempts/cooldown, lockout escalation, session lifetime |
| `PasswordHasherTest` | 12 | Hash/verify round trip, unique salts, cross-salt rejection, malformed Base64, iteration honouring, code digest, token shape |
| `RoomAuthRepositoryTest` | 20 | Real Room: hash-only storage, verification, lockout and expiry, code attempts/expiry/resend, reset, change, phone account, deletion, session expiry |
| `LocalizationParityTest` | 4 | Key parity, no blank values, placeholder parity, untranslated product name across three values directories |

Project totals: **84 JVM tests, 49 instrumentation tests** (24 `RoomTapLinkRepositoryTest` + 20 `RoomAuthRepositoryTest` + 5 `PlatformAndroidTest`). All passed through `connectedDebugAndroidTest`, and lint reported 0 errors and 56 warnings. Round 2 APK SHA-256: `165a458db3044d972b74b684affb153c03942742d13988ef19040ad9d3977720`.

### Installed-app checks

| Check | Observed result |
| --- | --- |
| Sign-up | Created `Chen Wei` / `chen.wei@ust.hk`; the verification screen showed the simulated code `502857` with a **Simulated** badge. |
| Verification | Verifying returned to Settings showing `Chen Wei`, `c***@ust.hk`, **Email verified**. |
| Stored credentials | The account row contained a Base64 hash, salt and iteration count; the plaintext password appeared in no column. |
| Sign-out and sign-in | Signing out returned to the welcome screen; the wrong password produced "That identifier and password do not match." without revealing whether the account existed; the correct password signed in. |
| Change password | Confirmed earlier in the same session; the form collapsed on success and the next sign-in used the new password only. |
| Traditional Chinese | Switching to 繁體中文 changed the top bar (設定), the theme value (跟隨系統), the bottom navigation (首頁／交易紀錄／探索／更多), the transaction dates (2026年11月15日) and the month heading (11月 2026) with no restart. |
| Simplified Chinese | Switching to 简体中文 produced 设置／账户／外观与卡／跟随系统, and the whole account flow — sign-up, verification, sign-in error and signed-in card — rendered in Simplified Chinese. |
| Date ordering defect found and fixed | The first build rendered "15 11月 2026" and an English "November 2026" heading. The heading exposed a real Compose defect: a plain `Locale.getDefault()` read is not observable, so the item was skipped. Dates now read `LocalAppLocale` and use year-first patterns in Chinese. |
| Widget and toast strings | Widget labels are present in both Chinese files and covered by the parity test; toasts resolve a localized context from the saved preference. |

### Defects found during this round

1. `widget_strings.xml` was outside the parity test's view because the test read only `strings.xml`. The test now reads every `*.xml` in each values directory, and the five widget labels were translated.
2. `AccountSection` read `vm.state.value` inside composition, which lint reports as `StateFlowValueCalledInComposition`; the busy flag is now collected.
3. A raw `&` in one string resource broke resource merging; the value is escaped and the build is green.
4. Dates and the month heading stayed in the previous language after a switch — see the row above.

### Limits of this round

- The account layer is single-profile by design: no ledger row carries an account id, so a second sign-in shows the same wallet. This is a stated limitation, not an untested behaviour.
- Code delivery is simulated; no email or SMS was sent. The SMTP/SMS path does not exist and is not claimed.
- Sessions are stored in plain `SharedPreferences`; Keystore-backed storage and database encryption were not implemented.
- The home-screen widget's static labels are translated, but its dynamic text (hidden-balance marker, status line, empty-card fallbacks) is still English; the widget was not placed on a launcher in this round.
- The money workflow was re-verified only structurally in this round (the wallet ledger and screens were unchanged); the HK$100 → HK$30 → reload → HK$120 sequence remains the round 1 evidence.
- The emulator was a `Small_Phone` AVD, not the Pixel 6 profile used in round 1. Neither is a physical device.

## Round 3: notifications, accessibility, contact and AI support (23 November 2026)

Verified on a macOS host with JDK 21, Gradle 8.10.2, AGP 8.7.2 and the same `Small_Phone` AVD running API 35 (720×1280, density 320). The database moved to version 4 through one additive migration (`MIGRATION_3_4`: eight preference columns, `messages.kind`, `messages.amountCents`); schema `4.json` is exported alongside 1–3.

**Round 3 result: debug APK built successfully; 94 JVM tests passed; 49 Android instrumentation tests passed; Android lint reported zero errors and 51 warnings.** The warning count fell from 56 to 51 because the round removed fourteen strings that nothing referenced (unused-resource warnings) and marked the one string that legitimately contains a `%` as non-formatting; every remaining warning is a dependency-version or plural suggestion, not a defect.

- Round 3 APK SHA-256: `95808f314083e258442a5a3b09f87b811fcea1381554b01f559e8eb0f2145b14` (58 MB debug build).
- Lint breakdown: 39 `GradleDependency`, 8 `PluralsCandidate`, 3 `AndroidGradlePluginVersion`, 1 `AppBundleLocaleChanges`.

### Automated coverage added or changed

| Suite | Passed | Main coverage |
| --- | ---: | --- |
| `SupportAssistantTest` (new) | 8 | Daily quota limit, Hong Kong midnight reset, history trimming and truncation, prompt hard rules, FAQ matching in both Chinese variants and English, unconfigured client, character budget |
| `PlatformPoliciesTest` (extended) | 11 | Master switch, independent per-category switches, scheduled-work gating with the new categories |
| `RoomTapLinkRepositoryTest` (extended) | 24 | The two low-balance cases now assert both the alert row and the new transaction row, and that a replayed operation does not add a second row |

Project totals: **94 JVM tests, 49 instrumentation tests**. `WalletRulesTest` 14, `WalletAnalyticsTest` 6, `MoneyTest` 3, `ShakeDetectorTest` 5, `WalletViewModelTest` 14, `AuthRulesTest` 17, `PasswordHasherTest` 12, `LocalizationParityTest` 4.

### Installed-app checks

| Check | Observed result |
| --- | --- |
| Swipe home cards | Swiping changed Campus (Card 1 of 3) → Commute (2 of 3) → Weekend (3 of 3, **FROZEN**); the dots, the arrows and the position line followed, and the "Current card" dropdown stayed in sync. |
| Notification settings, permission denied | The screen reported "System notifications are not allowed" and offered the Android settings shortcut. |
| Notification settings, permission granted | After `pm grant com.taplink.app android.permission.POST_NOTIFICATIONS` and reopening the screen it reported "System notifications are allowed". |
| Test notification | "Send a test notification" produced a real `taplink_test` notification: "Test notification — Notifications work on this device. Every alert is simulated and also saved in TapLink." |
| Transaction alert end to end | An HK$100 top-up through the UI produced "Top-up completed — HK$100.00 was added to your card. This is simulated money." with the subtext "TapLink · Simulated wall…", and one inbox row with the amount. |
| Inbox localisation | The same stored row rendered in Traditional Chinese: 「增值完成」「已為卡片加入 HK$100.00。此為模擬金額。」 |
| Contact us | WhatsApp 2520 1314 · Mon–Fri 09:00–18:00 with **Open**, the hotline with **Call**, the AI assistant, sign-language support, service hours, reply times and the prototype disclaimer. |
| AI support | "How do I top up at a store with no network" returned an English answer from `deepseek-flash` that stated the prototype has no real store top-up and handed account matters to WhatsApp 2520 1314; the counter went 20 → 19 → 18 and survived an app restart. |
| Secret refusal | Password- or code-shaped input is rejected in the view model before any request is built. |
| Sign language | Four chapters with complete transcripts; the player is disabled with "No video attached yet" because no clip ships. |
| Elderly mode on | One tap turned on large text (150%), high contrast, read-aloud and the simplified flow, with an "Elderly-friendly mode is on" banner; Home became balance, top up, pay, three records and a contact row. |
| Elderly mode off | One tap turned all four off — confirmed by reading `preferences.elderlyMode/largeText/highContrast/voiceReading` out of the app database, all four `0`. |
| Large-text layout | The activity row moved its amount under the merchant and date instead of squeezing three columns; the settings, inbox and chat screens stayed readable at 150%. |
| Traditional Chinese | 設定 / 外觀與卡 / 語言 / 訊息中心 / 增值完成 / 此為模擬金額, with the whole support and accessibility surface translated. |

### Defects found during this round

1. **The elderly master switch left the other three switches on.** Turning elderly mode off only cleared `elderlyMode`, so a user who expected the contrast to go back got an outlined UI with no banner explaining it. The switch is now symmetric — it turns the four on and off together — and the subtitle says so.
2. **Two instrumentation tests broke on the new inbox rows.** They asserted exactly one message per transaction and one asserted none; both now assert the payment row plus the presence or absence of the alert row.
3. **The transaction row broke at 150% text.** Merchant, date and amount fought over one line, so the date wrapped to three lines and the amount collided with it. The amount now drops onto its own line when `fontScale > 1.15`.
4. **The accessibility title did not fit the top bar** at 150% ("Accessibility & elderly mode" wrapped to two lines). The route now uses a short label, "Accessibility".
5. **The elderly switch row repeated itself** ("Elderly-friendly mode" heading, "Turn on elderly-friendly mode" label, then the description) and wrapped the label to three lines. It is now one title plus one description, and the "Simplified home" panel lost a switch that duplicated the master switch.
6. **The chat footer's last line sat under the pinned input row.** The list's bottom padding is now larger than the top, so the AI disclaimer never reads as a clipped sentence.
7. **An incremental dex merge failed** (`Type …$$ExternalSyntheticLambda0 is defined multiple times`) after several resource-only rebuilds. Clearing `app/build/intermediates/project_dex_archive` fixed it; the source was not at fault.

### Limits of this round

- **The DeepSeek API key is inside the APK.** It is read from git-ignored `local.properties` at build time. This is acceptable only for a marked demo; a release needs a server proxy. The key is not printed in this record or any other document.
- **Quota is per installation, not per account**, and is stored in `SharedPreferences`, so clearing app data resets it. There is no server-side enforcement.
- **No sign-language footage ships.** The player is real and enables itself when a clip is added, but with none present the chapters rely on their written transcripts.
- **Read-aloud depends on a Chinese speech engine.** It was confirmed to be invoked; the audio itself cannot be judged from a screenshot, and no engine-missing device was tested.
- **No TalkBack pass.** Semantics exist on hand-built controls but an exhaustive screen-reader audit was not performed, and this is not a certified accessibility assessment.
- **The hotline and WhatsApp hand-off were not actually used to contact anyone.** The intents were verified to be constructed and dispatched; no message was sent and no call was placed.
- **Only one AVD.** The emulator was a `Small_Phone` profile, not a physical phone, and not the Pixel 6 profile from round 1.
- Lint's remaining 51 warnings were reviewed and left in place deliberately; upgrading to the newest dependencies was out of scope for a submission week.

## Round 4: per-account wallets, account entry and Chinese input (23 November 2026)

Verified on the same host and the same `Small_Phone` AVD (API 35, 720×1280, density 320). The
database moved to version 5 through one additive migration (`MIGRATION_4_5`: `ownerId` on six wallet
tables, `app_meta`); schema `5.json` is exported alongside 1–4. This round started from two defects
reported after round 3 — a wallet that did not change when another account signed in, and Chinese
text that could not be typed into the support field — plus one request (an account entry in the
app bar).

**Round 4 result: debug APK built successfully; 94 JVM tests passed; 50 Android instrumentation tests
passed; Android lint reported zero errors and 51 warnings.**

- Round 4 APK SHA-256: `8bc469cf58500d2b55c81b3cbc91591b0ee06a6576877da268830bbd1abb0c8a` (56 MB debug build).
- Lint breakdown unchanged from round 3: 39 `GradleDependency`, 8 `PluralsCandidate`, 3 `AndroidGradlePluginVersion`, 1 `AppBundleLocaleChanges`.

### Automated coverage added or changed

| Suite | Passed | Main coverage |
| --- | ---: | --- |
| `RoomTapLinkRepositoryTest` (extended) | 25 | New `eachOwnerSeesOnlyItsOwnWallet`: the demo wallet keeps its three cards, account A starts with one empty card and reaches HK$50 after a top-up, account B stays empty, a cross-owner card id is rejected with `NOT_FOUND`, and switching back to the demo wallet restores the original three cards and balances |

Project totals: **94 JVM tests, 50 instrumentation tests**. No JVM suite changed in this round.

### Installed-app checks

| Check | Observed result |
| --- | --- |
| Migration on a populated version-4 database | After installing over the previous build, the app opened without a crash, the existing wallet appeared unchanged (Campus HK$428.50, Commute HK$270.00, Weekend HK$755.00) and every row carried the signed-in account's id in `ownerId`; `app_meta` held `legacy-wallet-adopted = 1`. |
| Account entry | The demo wallet showed a person glyph labelled "Account" at the leading edge of the app bar; a signed-in account showed its initial (H, then M) labelled "Account: <name>". Other screens kept Back in that slot. |
| New account seed | Signing up as a new account (verification code taken from the simulated inbox) opened a wallet with exactly one card, "My TapLink Card", at HK$0.00 and a new-wallet message — not the demo cards. |
| Write isolation | A HK$50 top-up made while the new account was signed in moved that card from HK$0.00 to HK$50.00, and a database dump showed the other account's three cards unchanged. |
| Switch back | Reopening the app in the same session restored the new account's HK$50.00 balance, so the wallet is persisted per owner rather than re-seeded. |
| Chinese input — diagnosis | The support screen reported "你的键盘没有中文输入…", and the "键盘设置" action opened Android's keyboard list, which showed `Gboard — English (US)` as the only layout. |
| Chinese input — after adding a keyboard | Adding 简体中文 (拼音) to Gboard made the notice disappear on return to the app (no restart), and a Chinese question typed with the pinyin keyboard was accepted, sent, and answered in Chinese. |
| Account screen | The new screen showed the account name, the masked identifier, the verification state, password change, sign out, deletion, and the note that cards, balances, transactions and messages belong to the signed-in account. |
| Receipt localisation | The receipt headline and the "transaction details" title now follow the interface language; with 简体中文 selected the receipt reads 已完成。 instead of "You're all set.". |

### Defects found during this round

1. **Accounts shared one wallet.** Every wallet table now carries `ownerId` and every query takes it. A new account is seeded with one empty card instead of the demo wallet, so two accounts cannot show the same balance.
2. **The upgrade would have hidden existing data or duplicated it.** The first version of the adoption rule ran whenever a signed-in owner had no cards, which on a fresh install handed the demo wallet to the first account that signed in. The rule now distinguishes a fresh database (no rows at first open: seed the demo wallet and never adopt) from an upgraded one (rows present: the first signed-in account adopts them once).
3. **Background work assumed the demo wallet.** The reminder worker, the content-refresh worker, the widget provider and the platform observers opened the shared repository without selecting an owner, so after a process restart they would have worked on the guest wallet. All four now resolve the owner from the stored session through `TapLinkContainer.activeOwner`, and the repository keeps the owner in a `StateFlow` so an already-collected flow switches instead of staying stale.
4. **Chinese could not be typed although the app was not at fault.** The device, not the field, was the problem: the only installed keyboard had an English layout, and a host input method does not reach the emulator. The app now detects that the enabled keyboards cannot type the chosen language, says so on the support screen, and links to the system keyboard settings; the notice refreshes on resume.
5. **The account screen repeated its heading** ("账户" as the page eyebrow, then again inside the panel). The panel now omits its own heading on the dedicated screen.
6. **Two user-visible strings were hard-coded English** on the receipt screen ("You're all set.", "Transaction details") and one fallback sentence on the empty receipt. They are now resources in all three locales.

### Limits of this round

- **Deleting an account keeps its wallet rows.** Only the credential row is removed, so re-registering the same identifier starts with an empty wallet and the old rows are unreachable from the app.
- **The boundary is enforced by the app's queries, not by the operating system.** A rooted device can still read the database file; this prevents a wrong wallet on screen, not a forensic attacker.
- **The adoption rule runs once per installation.** A device that upgrades while signed in keeps its history; a device that signs out before updating leaves the old rows under the demo wallet.
- **AI quota is still per installation, not per account**, so switching accounts does not reset the daily allowance.
- **The keyboard notice depends on the keyboard reporting its subtypes.** A vendor keyboard that reports none is treated as unknown and never nags the user.
- **No physical phone and no Chinese hardware keyboard.** Chinese input was verified with Gboard's 简体中文 (拼音) subtype on the emulator; handwriting, Cantonese and other input methods were not exercised.

## Round 5: page-heading system and chart typography (23 November 2026)

Verified on the same host and the same `Small_Phone` AVD (API 35, 720×1280, density 320). No
database version, migration or repository behaviour changed in this round: it is a presentation
round that answers six notes raised while looking at the running app — the top of a page was bare
text, the four tabs used different heading treatments, a subtitle was unwanted, headings kept a
full stop, and the text inside the Activity ring was both too large and too light.

**Round 5 result: debug APK built successfully; 94 JVM tests passed; 50 Android instrumentation tests
passed; Android lint reported zero errors and 51 warnings.**

- Round 5 APK SHA-256: `f0538d7b12a16a6eee1a1f7d573c3a4f3a2a7a122b987e32edeaa5c87d596424` (56.5 MB debug build).
- Lint breakdown unchanged from round 4: 39 `GradleDependency`, 8 `PluralsCandidate`, 3 `AndroidGradlePluginVersion`, 1 `AppBundleLocaleChanges`.

### Automated coverage added or changed

No suite changed. `LocalizationParityTest` (4 cases) continued to pass, which is the guard that
matters for this round: deleting the unwanted Home subtitle and editing the Home headline kept all
three locales at the same key set in both languages.

Project totals: **94 JVM tests, 50 instrumentation tests**.

### Installed-app checks

| Check | Observed result |
| --- | --- |
| Home heading | The eyebrow "YOUR EVERYDAY WALLET" sits in a pill on a tinted card, the headline "A little tap, a lot closer" is semi-bold, and no subtitle line follows it (screenshot 36). |
| Tab-root consistency | Activity, Explore and More open on the same card form: pill eyebrow, semi-bold headline, muted subtitle (screenshots 37–39). Drill-down screens (payment, settings, auth) keep the lighter heading, so the card marks a tab root rather than decorating everything. |
| Title punctuation | "Know where it goes", "Your city, connected", "Make it yours", "Stay in the loop", "Check, then confirm", "More journeys, one card" and "Small treats, on us" now render without a trailing full stop; subtitles below them keep theirs. |
| Activity ring | The centre reads "This month" in a bold face with "Debits only" in a medium face under it, both inside the inner diameter of the stroke (screenshot 37); the same layout holds at 150% text. |
| Chinese punctuation | With 简体中文 (拼音) enabled, the comma key produced `，` and the period key produced `。` in the support field; the question "怎么充值。" was accepted, sent, and answered, so the field itself does not filter or normalise punctuation (screenshot 40). |
| Keyboard coverage for the three interface languages | The test device was prepared with `English (US) (QWERTY)`, `简体中文 (拼音)` and `繁體中文 (台灣, 注音)` in Gboard, so the Traditional Chinese interface can be typed into with a Traditional layout instead of a Simplified one (screenshot 41). |
| Elderly mode | Not re-checked on the device this round: `Dashboard` passes `home_simple_subtitle` for the simplified greeting and `null` only for the standard one, so the simplified Home still shows its explanatory line. |

### Defects found during this round

1. **The Home headline kept a full stop in the middle.** "A little tap. A lot closer." read like two
   sentences; it is now "A little tap, a lot closer" — one phrase, no full stop.
2. **Each page invented its own heading treatment.** Home had a tinted card, the other tabs had bare
   text on the page background. The card form moved into `PageHeading` as a flag and is used by all
   four tab roots.
3. **A title rule was about to be applied by hand.** Rather than editing fifteen strings in three
   locales, `PageHeading` drops a trailing `.` or `。` from the title it is given, so a new screen
   inherits the rule and cannot forget it; subtitles are untouched because they are sentences.
4. **The ring text overflowed and then under-weighted.** Fitting the words to the inner diameter made
   them small and, at a light weight, hard to read; the headline now uses a bold face (the month is
   what the screen is about) and the note a medium face, so the two lines differ without the top line
   disappearing.
5. **The unwanted subtitle was a resource, not a literal.** Removing it meant removing the reference
   *and* the string in three locales, otherwise the parity test would have flagged an orphan key.

### Limits of this round

- **Typography was judged from screenshots, not by a designer.** The hierarchy is defensible (one
  eyebrow, one headline, one supporting line) but it is not an accessibility audit of colour contrast
  on the tinted hero surface; the high-contrast theme still adds its border to that surface.
- **The heading card is applied to the four tab roots only.** Deeper screens keep the previous,
  lighter heading on purpose; a reviewer who expects the card everywhere will read that as
  inconsistency rather than hierarchy.
- **Chinese punctuation was verified with Gboard's 简体中文 (拼音) subtype.** Stroke, handwriting and
  Cantonese keyboards were not exercised, and a vendor keyboard that maps punctuation differently is
  outside what was tested.
- **The Traditional Chinese keyboard was added but not typed through automatically.** 繁體中文 (台灣)
  offers 注音 and handwriting, and neither can be driven by a scripted tap sequence that would be
  trustworthy evidence; what is recorded is that the layout exists, that the app's notice no longer
  applies, and that the field already accepts Chinese and full-width punctuation unchanged.
- **The app's keyboard check is per language, not per script.** It asks whether any enabled keyboard
  can type `zh`, so a device with only 简体中文 still passes the check while the interface is in
  繁體中文. Refusing that combination would nag users whose keyboard can output both.
- **No visual regression test.** Nothing in the suite would fail if a heading lost its pill again;
  the screenshots are the record.



Independent [data review](reviews/DATA_REVIEW.md) and [UI review](reviews/UI_REVIEW.md) findings were corrected and re-reviewed. Regression coverage includes tied ledger timestamps, committed-result cancellation, stale preference writes, stale receipt results, recreated-dialog completion state and obsolete location callbacks. The final visual pass additionally corrected system-bar contrast.

The following are implemented but not fully verified through physical hardware or long-duration execution: real launcher widget placement and refresh after process death; physical shake sensitivity; successful real network-provider location; OS notification/channel delivery combinations; six-/24-hour WorkManager scheduling under power restrictions. No physical phone, API 29 device, exhaustive accessibility pass, or in-flight Activity/process-death stress campaign was available. QR scanning is intentionally omitted; manual merchant entry is implemented. The embedded map is a labelled seed-data overview.

See [feature status](FEATURE_STATUS.md) for feature-specific boundaries, [screenshots](SCREENSHOTS.md) for visible results, and the repository's Actions page for independent Linux build status. A workflow definition or queued run is not itself a passing result.
