# TapLink independent UI/state review

Review date: 2026-11-22. Status at 21:40 HKT: UI-01 through UI-04 are resolved in the reviewed source and pass the relevant isolated ViewModel probes. No unresolved finding remains from this review. Android lifecycle/device validation remains with main; this is not full Android runtime certification.

Scope: `ui/**`, `MainActivity.kt`, the UI/platform hooks, `API_CONTRACT.md`, and `../proposal/TapLink_Proposal_Assets_EN/source/feature_requirements.json`. Repository code is read only to establish UI integration semantics; the separate data review owns data-layer correctness. Only this report is edited. No Gradle, emulator, ADB, browser, or computer actions were run.

UI-01/UI-02 were reproduced with the actual `WalletViewModel.kt` and domain sources in an isolated JVM probe, using a fake repository and minimal lifecycle/saved-state adapters. Their fixes and the later UI-03/UI-04 fixes passed targeted probes. UI-03's screen recreation is represented by replacing Flow collectors on the retained ViewModel, not by running Android/Compose. None is claimed as a device reproduction.

## Initial urgent findings — resolved in targeted re-review

### UI-01 — P1: preference mutations can restore an old selected card or undo a saved preference

**Status: resolved; retain the original sequence as regression coverage. Line references in this finding describe the initial implementation; current fix locations are below.**

- Locations: `app/src/main/java/com/taplink/app/ui/WalletViewModel.kt:141-142`; `app/src/main/java/com/taplink/app/ui/MoreScreens.kt:103`. Relevant completion ordering: `WalletViewModel.kt:127-138`; replacement semantics: `data/RoomTapLinkRepository.kt:73-83`.
- Sequence: start with card A selected and preferences emitted to the ViewModel. Select B and let `selectCard` return successfully, but hold the next preferences Flow emission. Once `busy` clears, toggle balance visibility or another setting. `updatePreferences(change(preferences.value))` still copies A, and the repository saves the entire object, resetting the selection to A. Similarly, saving two independent toggles before the first emission arrives can undo the first toggle. The latter can re-enable widget balance visibility or reminders after the user disabled them.
- Expected: a change to one setting preserves every other committed setting and the latest selected card. The mutation completion and subsequent UI readiness must not rely on Room invalidation having already delivered an observation.
- Actual: `busy` serializes mutation calls but not the mutation-to-observation gap. Both ordinary switches and Save amounts use a cached full-object snapshot; the repository intentionally replaces it.
- Fix direction: use a serialized, authoritative read/modify/write path (prefer an atomic preferences transformation in the repository), and route Save amounts through the same path. Do not treat `stateIn.value` as authoritative write state. Delay emissions in a regression test and verify both changes survive.

### UI-02 — P1: a stale detail read can replace a newer success receipt

**Status: resolved; retain the original sequence as regression coverage. Line references in this finding describe the initial implementation; current fix locations are below.**

- Locations: `app/src/main/java/com/taplink/app/ui/WalletViewModel.kt:143-149`; similar unguarded restore at `WalletViewModel.kt:44-46`; consumer `app/src/main/java/com/taplink/app/ui/PaymentScreens.kt:87-90,118-120`.
- Sequence: open transaction A with `getReceipt(A)` held pending. Back out, start and confirm a new top-up/payment B, and let B finish. Release the old A lookup. Its coroutine writes A to the single `receipt` state without checking the current route or operation. The success screen selects its transaction directly from that state and now shows/shares A, despite just completing B. Opening A then B details with reversed lookup completion also leaves B's enrichment/share state missing because A replaces B's receipt.
- Expected: only the lookup for the current destination/request may update its receipt. Successful B must continue to show B's amount, card, timestamp and balance.
- Actual: detail lookups are not tracked/cancelled or associated with a destination generation. `startOperation` clears the shared receipt but cannot invalidate the old coroutine. Detail rendering has a matching check; the success screen has no operation-ID check.
- Fix direction: invalidate/cancel pending detail and restore requests on destination/operation changes, check the request identity after suspension, and bind success rendering to `receiptOperation`. Test out-of-order completion explicitly.

## Additional initial findings — resolved in second targeted re-review

### UI-03 — P2: rotating during card creation leaves the restored dialog open after success

**Status: resolved in the 21:39 patch; targeted success/error/collector-replacement checks passed. References and sequence below describe the pre-fix source.**

- Locations: `app/src/main/java/com/taplink/app/ui/Dashboard.kt:28-30,94-98`; asynchronous callback execution now at `app/src/main/java/com/taplink/app/ui/WalletViewModel.kt:229-240`. The same pattern occurs in the redemption dialog at `ui/TransportOffers.kt:67,99`.
- Sequence: Home > New > enter a name > Confirm. Hold the repository completion, recreate the Activity, then complete `createCard` successfully. The dialog is restored through `rememberSaveable`, while the retained ViewModel's `after` lambda still captures the old composition's `dialog` state object. It clears that old object, not the restored screen state. Once `busy` becomes false, the restored Create dialog is still actionable. Confirming again creates another same-name card; card creation has no operation-ID replay guard. Redeeming an offer across recreation similarly leaves a stale confirmation dialog whose next attempt gets Already redeemed.
- Expected: successful completion closes the currently displayed dialog exactly once, including after recreation; an error preserves the current draft.
- Actual by source: durable mutation lifetime is in the ViewModel but completion writes to the old composition's transient state. Saving the initial dialog value alone does not connect it to later completion.
- Fix direction: keep the dialog action/draft and its completion identity in ViewModel/saved state, or consume a keyed completion state in the current composition. Do not retain a completion callback that mutates a disposed screen. Verify with Activity recreation and a gated create/redeem result, not only rotation while idle.

### UI-04 — P2: a pending location fix silently overrides a newer manual district choice

**Status: resolved in the 21:39 patch; delayed-callback/manual-choice/navigation/disposal checks passed. References and sequence below describe the pre-fix source.**

- Locations: `app/src/main/java/com/taplink/app/ui/ExploreScreen.kt:38,47-49`; platform callback delivery at `app/src/main/java/com/taplink/app/MainActivity.kt:71-82`.
- Sequence: Explore > Use approximate location, with permission already granted and a fix still pending. Before the fix returns, select another Manual district. The dropdown is enabled and clears latitude/longitude, saves the new district, and says it is showing that district. Deliver the earlier location callback. It assigns latitude/longitude unconditionally. The `latitude != null` branch now bypasses the chosen district and shows locations from every district sorted by the earlier request.
- Expected: the latest explicit discovery mode/district choice wins. An earlier approximate-location request must not silently override it.
- Actual: selecting a district neither cancels nor invalidates the in-flight callback. The saved district remains visible in the dropdown while the result list no longer respects it.
- Fix direction: use a request generation/mode token and invalidate it when switching to manual mode or leaving the screen; ignore obsolete completions. Test a delayed callback followed by a manual choice. Also verify permission-result delivery across recreation, because the Activity callback is not durable.

## Executed evidence and limits

An isolated Kotlin 2.0.21 / kotlinx-coroutines 1.9.0 probe compiled the actual ViewModel, `Models.kt`, `Money.kt`, and repository interface with JDK 21. Temporary adapters provide `ViewModel`, a scope, and the string/Flow operations of `SavedStateHandle`; the fake repository intentionally holds preference emissions and one receipt lookup. No Compose, Android, Room, Gradle, app storage, or device was invoked. This tests the ViewModel interleavings, not Android lifecycle behavior or the likelihood of a particular Room scheduling order.

Output from the initial implementation:

```text
UI-01: selected B successfully, then changed visibility; committed selected card=A, visibility=false
UI-02: completed card B HK$50.00, then released old detail; route=Receipt, receipt card=A, receipt cents=1234, matches completed request=false
```

The second sequence renders the old card A/HK$12.34 receipt after B/HK$50.00 completed. The underlying debit/credit is not changed by this UI race. Probe fixtures and output are outside the repository in the system temporary directory; only this report is changed in the project.

## Areas checked without another material finding

- Operation card, merchant, amount, and operation ID live in `SavedStateHandle`. Confirm copies inputs into local variables before suspension. `busy` is set synchronously, suppresses duplicate submissions/navigation, and successful confirmation replaces the back stack with Home. Ordinary Back from a receipt cannot re-enter the completed confirmation. Same-screen retries retain the operation ID.
- History applies card/category/type/query plus inclusive start/exclusive next-day-end bounds in Hong Kong time. End-date selection is interpreted as a calendar day in UTC by the picker, then converted to Hong Kong bounds; it does not accidentally shift the selected day. Reversed bounds get feedback. Chart monthly aggregation uses the filtered debit records, and the chart explicitly excludes credits/reloads. The month controls are labelled as monthly spending, not an implicit history-list filter.
- Charts have category amounts and a textual accessibility description; the source alone does not establish unusable colors or clipping. Large-font, narrow-window, maximum-balance, dark-mode, and TalkBack checks remain device validation, not style findings.
- QR scanning is absent from `MainActivity` and the null hook omits its control; there is no fake scan success. Map/share/widget actions have fallback feedback, and the UI labels simulated balances, coupons, passes, subsidies, locations, and map content. Sensor unavailability should be checked on a device; the on-screen balance toggle remains available.
- Subsidy review initially lacked an amount, but the author added the captured estimate during this review (`TransportOffers.kt:48`, `PaymentScreens.kt:63-64`). This was active implementation, so it is not reported as an open bug.

## Targeted validation still needed

At initial review, the inspected test tree contained domain/data/platform tests, but no ViewModel or Compose UI tests. That inventory is historical; this reviewer did not rerun the authors' subsequent test suite. Isolated probe results are recorded below. Main's Android validation should cover:

1. Delayed preferences emission after select-card, after widget privacy toggle, and after Save amounts; every independent change must persist.
2. Detail A → back → detail B with reversed completions; detail A → new successful operation B; stale restore completion; receipt operation identity must stay correct.
3. Recreation during create-card and offer redemption; form/review/filter recreation; process death around confirmation with reconciliation by the original operation ID. Cancellation after commit is covered by the separate data review and must not be interpreted by the UI as proof that nothing committed.
4. Delayed location fix after manual selection; rotation during location permission prompt; leave/re-enter Explore before delivery.

## Targeted re-review of UI-01/UI-02 fixes

Re-read the arriving implementation and recompiled the actual updated ViewModel/domain sources. The fake repository was extended with the new atomic-transform contract; receipt delivery was deliberately made **non-cancellable** using a held continuation, so cancellation alone could not make the race test pass.

- **UI-01:** `WalletViewModel.kt:243-244` now calls `repository.transformPreferences(change)`. `MoreScreens.kt:103` routes Save amounts through it. The repository transforms the latest stored preferences within `mutate` (`RoomTapLinkRepository.kt:82-92,266-269`), which wraps the operation in one Room transaction. A search found no remaining full-model preference write in `ui/**` or `MainActivity`. The probe holds every preferences emission while selecting B, hiding the in-app balance, hiding the widget balance, and saving new thresholds. All committed changes survive. The repository transaction itself was inspected, not run in this probe.
- **UI-02:** `WalletViewModel.kt:67-95` cancels lookups, increments a generation, and checks generation + route + operation after the suspension. Navigation and new/successful operations invalidate previous lookups (`97-138,199-208`). Receipt rendering and sharing now use `expectedOperation` (`PaymentScreens.kt:93-97,127-129`). All three tested orderings preserve the intended receipt: old detail → new success; old detail → newer detail; old restored receipt → new success. No replacement by the old result and no incorrect loading state was observed.

Output:

```text
UI-01 FIX PASS: delayed preference emissions preserve selected B, balance hidden, widget hidden, and saved amounts
UI-02 FIX PASS: cancellation-ignoring old detail cannot replace card B HK$50.00 success receipt
UI-02 FIX PASS: reverse detail completion preserves current B receipt and loading state
UI-02 FIX PASS: stale restored-receipt lookup cannot replace a newer completed operation
```

The updated ViewModel also adds PENDING/UNCERTAIN outcome reconciliation and retains the original operation identity. This was read for integration, but the new cancellation/process-death paths were not comprehensively executed by this bounded re-review; keep the lifecycle cases above on main's test plan. At this first re-review (21:38 HKT), UI-03/UI-04 were unchanged; their subsequent fixes are checked below.

First re-review source identity at 21:38 HKT (SHA-256; repository-relative paths):

```text
app/src/main/java/com/taplink/app/ui/WalletViewModel.kt
1350B9C4494F00199A97501343A6818CB2FBF827EC696F96ABE11B6874454B4B
app/src/main/java/com/taplink/app/ui/PaymentScreens.kt
0757F5175C7751C0ED4C7F005A57C5246BFC2A25939297484DBEF8434BB236B7
app/src/main/java/com/taplink/app/ui/MoreScreens.kt
474B28207261760829865A4796AA056DA2DE1104F4FF2428575545AD3DA294AA
app/src/main/java/com/taplink/app/data/RoomTapLinkRepository.kt
499E5CA5F255811EA544692D6667CD84569A74249325482EB79CA15A1DF77498
app/src/main/java/com/taplink/app/ui/Dashboard.kt
D8AFAD8B68D013BD1AF897DA6199900BBF5BCECAA8D8D7A9EB58FA5541B0A60F
app/src/main/java/com/taplink/app/ui/ExploreScreen.kt
4312C5FDEA4EC7271EE5A63CFA7CA50EAF1F6F9D6F2E45FC8DB0E0F15A97E77C
app/src/main/java/com/taplink/app/ui/TransportOffers.kt
05AD5E8284F174AF21005305035842B1D926B0CBD3EFF65769807355237F2AE5
```

## Second targeted re-review: UI-03/UI-04

Requested scope was only these two fixes. The relevant files were stable at the 21:39:16 write timestamp throughout the check. No broad review, Gradle run, D8 invocation, emulator, or device action was performed.

- **UI-03 resolved:** card/offer dialog state now lives in `SavedStateHandle` (`WalletViewModel.kt:38-40`); submission captures the action/target/name and a dialog token, and completion clears that shared state only for the matching token (`257-295`). Dashboard observes the shared dialog/name and invokes ViewModel methods (`Dashboard.kt:27-28,85-93`); offers use the same approach (`TransportOffers.kt:67,94-99`). No mutation completion captures a composition-local dialog object. With a gated fake create/redeem result, replacing the observer before completing success closes the replacement observer's dialog. Calling submit again after completion causes no second repository call. Failed creation retains the current draft and open dialog.
- **UI-04 resolved:** `requestLocation` captures a generation and accepts a result only when that generation is current and the route is Explore (`WalletViewModel.kt:298-317`). Manual selection cancels the generation and clears both coordinates before saving the district (`318-328`); navigation also invalidates pending requests (`105-123`). Explore invalidates on disposal and invokes the new platform cancellation hook on disposal/manual selection (`ExploreScreen.kt:41-42,53-55`). `MainActivity.kt:53` clears/cancels the callback/job, and `72-74` ignores permission completion when no callback remains. The probe deliberately delivers callbacks after invalidation: manual state survives, a previous visit cannot clear a newer request's loading state, and cancellation allows a fresh request to complete.

Executed against the actual updated ViewModel/domain sources with the same JVM adapters/fake repository described above:

```text
UI-03 FIX PASS: replacement card-dialog collector closes on delayed success; stale submit creates no duplicate
UI-03 FIX PASS: failed creation preserves the current dialog and draft
UI-03 FIX PASS: replacement offer-dialog collector closes on delayed success; stale submit cannot redeem again
UI-04 FIX PASS: manual district, empty coordinates and status survive a delayed previous fix
UI-04 FIX PASS: leave/re-enter invalidates prior callback without cancelling the current request state
UI-04 FIX PASS: disposal invalidation ignores old result and permits a fresh location request
```

**Limits:** these checks execute ViewModel behavior and inspect the Compose/platform wiring. They do not execute Android Activity recreation, SavedStateRegistry serialization, permission dialogs, sensor/location providers, or Room. Main should still rotate during a delayed create/redeem and during a permission prompt on the device. Process death during an in-flight card creation is a separate case from this fixed rotation issue and was not certified here.

Second re-review source identity at 21:40 HKT (SHA-256; repository-relative paths):

```text
app/src/main/java/com/taplink/app/ui/WalletViewModel.kt
333BB9C30AF1AB8CC8E160628E281C0546CB7C498DC63FD93F7463DAB58C148C
app/src/main/java/com/taplink/app/ui/Dashboard.kt
5540D759C36042900447BCCADC93F50E506942541B0B501A8BFE2898FBD4DAF7
app/src/main/java/com/taplink/app/ui/ExploreScreen.kt
EB7722486BE7A137B5C5B08ED6028E556B60ECAD6EBBFB621781A173DDC6E3B3
app/src/main/java/com/taplink/app/ui/TransportOffers.kt
21AE14B21239C39FE5A07B7EF125A91CC5DE5A29DD419439A4157116DA8F927C
app/src/main/java/com/taplink/app/ui/PlatformActions.kt
2E908A7B64066A7AB216584C0ADBF35D0B126E91F60178771200A90F960CAFFA
app/src/main/java/com/taplink/app/MainActivity.kt
35FCC941E32D4F620AA169A2EF24B6AD2EA62CFADAC7ADDBEF2A838EC61D3CB6
```

## Handoff

All four findings are resolved in the reviewed patches; their targeted checks pass with the limits above. Main owns the full build and device validation. Only this report was edited in the repository; all probes remain in the system temporary directory.
