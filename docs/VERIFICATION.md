# Verification record

Verified on 22 November 2026 (Hong Kong time). Implementation revision: `97b06db`.

**Result: debug APK built successfully; 51 JVM tests passed; 29 Android instrumentation tests passed; Android lint reported zero errors and zero warnings.** The installed wallet workflow was also checked against its persisted ledger. These results apply to the scope below, not every possible device or lifecycle interleaving.

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
| RoomTapLinkRepositoryTest | 24 | Real Room transactions, concurrent/idempotent operations, injected write failures, cancellation, stable ledger order, preference transforms, offers/passes/subsidies and reopen |
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

## Review and remaining limits

Independent [data review](reviews/DATA_REVIEW.md) and [UI review](reviews/UI_REVIEW.md) findings were corrected and re-reviewed. Regression coverage includes tied ledger timestamps, committed-result cancellation, stale preference writes, stale receipt results, recreated-dialog completion state and obsolete location callbacks. The final visual pass additionally corrected system-bar contrast.

The following are implemented but not fully verified through physical hardware or long-duration execution: real launcher widget placement and refresh after process death; physical shake sensitivity; successful real network-provider location; OS notification/channel delivery combinations; six-/24-hour WorkManager scheduling under power restrictions. No physical phone, API 29 device, exhaustive accessibility pass, or in-flight Activity/process-death stress campaign was available. QR scanning is intentionally omitted; manual merchant entry is implemented. The embedded map is a labelled seed-data overview.

See [feature status](FEATURE_STATUS.md) for feature-specific boundaries, [screenshots](SCREENSHOTS.md) for visible results, and the repository's Actions page for independent Linux build status. A workflow definition or queued run is not itself a passing result.
