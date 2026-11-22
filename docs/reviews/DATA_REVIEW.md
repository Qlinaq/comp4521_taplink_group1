# Independent data/domain review

Reviewed 2026-11-22 against the working tree, while implementation was still in progress. Owner: Maxwell (`01a2036a-2c68-7602-aa3f-28a94ef849fb`); main assigns fixes. This review changes only this report. No application code, Gradle invocation, device/UI action, remote change, or additional agent was used.

**Final targeted status: D1, D2, and atomic preference updates are resolved.** Independent source re-review found the intended fixes intact, and main's supplied instrumentation log confirms the relevant Room cases passed. No open defect remains among these three review items. The conditional old-schema upgrade limitation below remains outside this closure.

## Targeted re-review — 2026-11-22

Waited for the owner's changes and confirmation that the fixes were saved, then inspected only D1, D2, the new atomic preference API, their targeted tests, and the directly related UI call sites. After main supplied runtime results, checked the targeted source again and read the existing execution log. Data/domain implementation and Room test hashes are unchanged from the first targeted re-review; the current contract and relevant updated UI lines were reread. No new broad review, Gradle invocation, independent probe, or test execution was performed in either re-review. The original findings and reproductions below are retained as historical evidence, not current open defects.

| Item | Status | Evidence in the revised files |
| --- | --- | --- |
| D1: equal-timestamp ledger order | **Resolved; targeted Room cases passed in main's run.** | `data/Entities.kt:16-26` adds SQLite-generated `ledgerSequence` as the primary key while preserving a unique transaction `id` and the existing operation/type constraint. Both queries use `createdAt DESC, ledgerSequence DESC` (`data/TapLinkDao.kt:18,60`). Debit insertion still precedes reload insertion within the same transaction. |
| D2: cancellation/commit contract | **Resolved; late-cancellation recovery passed in main's run.** | `API_CONTRACT.md:56-60,152` explicitly distinguishes rollback from losing the response after commit, requires retaining the original ID **and inputs**, and describes receipt recovery/same-ID replay. `domain/TapLinkRepository.kt:5-9` and `data/RoomTapLinkRepository.kt:270-273` reinforce that cancellation does not imply failure. |
| Atomic preference changes | **Resolved; stale/concurrent/invalid-transform Room cases passed in main's run.** | `data/RoomTapLinkRepository.kt:82-92` reads the latest preferences, invokes the transform, validates, and saves inside `mutate`'s transaction. `ui/WalletViewModel.kt:254-255` forwards the transform instead of transforming a collected snapshot. Settings, amount settings, district selection, visibility toggling, and the shake callback copy the callback argument. The old whole-model API remains explicitly documented as replacement (`API_CONTRACT.md:52`). |

Paths in this table are relative to `app/src/main/java/com/taplink/app/`, except `API_CONTRACT.md`.

**Targeted test review:** `app/src/androidTest/java/com/taplink/app/data/RoomTapLinkRepositoryTest.kt` now adds:

- Lines 144-145, 152-172 and 175-185: reload-after-debit ordering, adversarial lexical IDs, agreement between flow and direct DAO query, and successive repository operations under a fixed clock. These cover both failure modes in D1 rather than relying on randomly favorable UUIDs.
- Lines 304-337: a real `repository.pay` commits while return delivery is paused; a separate read proves the receipt/balance committed; cancelling the original caller then delivers cancellation; replay of the original ID returns that receipt with exactly one debit. This covers D2's missing window. The separate before-commit rollback test remains.
- Lines 94-131: preserving a newer selected card despite a stale UI snapshot, concurrent independent transforms alongside card selection, and rejection of invalid settings/card IDs without partial preference changes.

**Direct D2 caller check:** the current `WalletViewModel.kt` retains unresolved inputs (`51-54`), reconciles on initialization (`62-66`), blocks replacement attempts (`131-136`), handles cancellation as uncertain (`196-201`), and performs bounded receipt lookup without repeating the mutation (`223-232`). This is a focused source check, not a new UI/lifecycle test certification.

### Main's executed evidence — separately attributed

Main reports a successful full build and **51 passing unit tests**, plus execution on **Android API 35**. This reviewer did not execute those commands or inspect a separate build/unit-test log; those results and the device API level are attributed to main's supplied handoff.

This reviewer did read [main's instrumentation log](C:/Users/UZI/AppData/Local/Temp/taplink_instrument1.log:413), last written 2026-11-22 21:46:22 local time. It records **`OK (29 tests)`** at line 413 and final `INSTRUMENTATION_CODE: -1` at line 416. Parsing the completed per-test records gives **24 Room tests and 5 platform tests**, all with success status `0`, and no failed completed result. The targeted passing records are:

| Review item | Passing cases in the supplied log | Success-status line(s) |
| --- | --- | --- |
| D1 | `equalTimestampLedgerOrderDoesNotDependOnLexicalIds`; `affordablePaymentCommitsDebitOneReloadAndFinalBalanceAlert`; `successiveRepositoryOperationsWithFixedClockUseCommitOrder` | 15, 43, 267 |
| D2 | `cancellationAfterCommitRecoversOriginalReceiptWithoutAnotherDebit` | 57 |
| Atomic preferences | `invalidPreferenceTransformRollsBackAllItsChanges`; `preferenceTransformUsesLatestSelectionEvenWhenUiSnapshotIsStale`; `concurrentPreferenceTransformsPreserveIndependentFieldsAndCardSelection` | 239, 295, 309 |

These are main's executed runtime results, independently checked against the supplied log, combined with this reviewer's source analysis. Reading/parsing the log did not run tests. Runtime verification of these targeted repository fixes is no longer pending.

**Remaining limits, not open D1/D2/preference findings:**

- D1 changes the stored transaction schema while `TapLinkDatabase.kt:10` remains version 1 and `TapLinkContainer.kt:12-13` registers no migration. The ordering fix is correct for a database created with the revised schema. If an earlier pre-sequence version-1 database was installed, an in-place upgrade needs a migration/version decision; reopening such an old database is not covered by the current same-schema reopen test. Whether that earlier development schema was ever installed was not established here. Do not interpret this closure as upgrade-compatibility approval.
- Independent flows still do not promise one atomic combined wallet snapshot. That original limitation was outside the requested fixes and was not reopened or retested.

Re-reviewed SHA-256 snapshots:

```text
API_CONTRACT.md
734068CC4E41EB55C726A902B2B550F74204A622492669D9471F9C454E39B9AC
app/src/main/java/com/taplink/app/data/Entities.kt
4810F156F8A178E3E9A6C56911882C60CB85DD8DFE219A8BEB4DCFD0CFD45BA3
app/src/main/java/com/taplink/app/data/TapLinkDao.kt
6B57F1BEB14086E4FB7DB1425E921139A2D8E39B1832E6C7C6D70FA6BDAE657D
app/src/main/java/com/taplink/app/data/RoomTapLinkRepository.kt
499E5CA5F255811EA544692D6667CD84569A74249325482EB79CA15A1DF77498
app/src/main/java/com/taplink/app/domain/TapLinkRepository.kt
0E00954BA4F8E8482DBF374259B0144D2D4F9D6DA17264E4F9C028C8EED7066A
app/src/androidTest/java/com/taplink/app/data/RoomTapLinkRepositoryTest.kt
CD66C02F4A3C2A3EE23C1E99A5B782D48B9E8E051FC2D7C4E8CD71EB34766DE7
app/src/main/java/com/taplink/app/ui/WalletViewModel.kt
333BB9C30AF1AB8CC8E160628E281C0546CB7C498DC63FD93F7463DAB58C148C
```

## Original review evidence

The original review identified two P2 findings and ran only the isolated SQL/coroutine checks documented below. Source locations and present-tense descriptions in the remaining sections refer to the original snapshot unless explicitly marked otherwise.

## D1 — P2: preserve ledger order when timestamps tie

**Locations:** `app/src/main/java/com/taplink/app/data/TapLinkDao.kt:18-19,60-61`; `app/src/main/java/com/taplink/app/data/RoomTapLinkRepository.kt:118-124,139-141,271`. Contract: `API_CONTRACT.md:20,141`.

The debit and its subsequent automatic reload receive the same `now`, but both transaction queries break timestamp ties using random UUIDs (`id DESC`). UUID order does not record which balance transition happened last. This affects every reload operation probabilistically and separate operations occurring within the same millisecond. A fixed injected clock makes the latter common in tests.

**Reproduce:** start with 6,000 cents, enable the default reload, and pay 2,000 cents. The payment records 4,000 cents; the subsequent reload records 14,000 cents. Whenever the payment UUID sorts after the reload UUID, the newest-first flow lists the payment first. Repeat until this UUID ordering occurs, or inject these equivalent rows into an isolated query fixture:

```sql
CREATE TABLE transactions (
  id TEXT, type TEXT, createdAt INTEGER, balanceAfterCents INTEGER
);
INSERT INTO transactions VALUES
  ('f-payment', 'PAYMENT', 1, 4000),
  ('1-reload', 'AUTO_RELOAD', 1, 14000);
SELECT type, balanceAfterCents FROM transactions
ORDER BY createdAt DESC, id DESC;
```

**Expected:** `AUTO_RELOAD 14000`, then `PAYMENT 4000`: the newest balance transition appears first.

**Actual:** `PAYMENT 4000`, then `AUTO_RELOAD 14000`. Verified with Python's in-memory SQLite using the exact DAO ordering. The short fixture IDs only make the lexical relationship obvious; real UUIDs can have the same relationship.

**Impact:** persisted balances and receipt totals remain correct, but the history's apparent running-balance progression is wrong. A latest-history entry can show the pre-reload balance while the card shows the post-reload balance. Sorting by transaction type alone would fix only the within-operation case, not equal-time separate operations.

**Suggested direction:** persist an ordering value that captures committed ledger sequence and use it consistently for both queries. Do not depend on UUID lexical order or artificially move a reload into a future timestamp. Add an assertion for debit/reload ordering and for successive operations with a fixed clock. The current reload test (`RoomTapLinkRepositoryTest.kt:86-99`) checks count, values, and replay, but not order.

## D2 — P2: cancellation after commit contradicts the rollback guarantee

**Locations:** `app/src/main/java/com/taplink/app/data/RoomTapLinkRepository.kt:257-262`; `API_CONTRACT.md:141`; related test `app/src/androidTest/java/com/taplink/app/data/RoomTapLinkRepositoryTest.kt:203-215`.

`mutate` wraps the entire operation in `withContext(Dispatchers.IO)`. After `database.withTransaction { block() }` has committed, delivery of its result back to a different caller dispatcher remains cancellable. Cancellation in that window discards success and delivers `CancellationException`; the completed database commit cannot be rolled back by the catch/rethrow. The contract currently promises that cancelled operations leave no ledger or balance changes.

**Reproduce deterministically:** use a caller dispatcher whose queued continuations can be paused; start a mutation, let the IO transaction finish, pause its return continuation, cancel the caller job, then run the queued return continuation. Read the receipt/balance with a separate, uncancelled scope.

**Expected under the written contract:** a cancelled call has no committed operation or balance change.

**Actual by the implementation's dispatch semantics:** the caller receives cancellation despite a completed commit. Retrying with the same operation ID remains safe; treating cancellation as proof of failure and using a new ID can commit a second intended payment/top-up.

**Executed evidence:** an isolated Kotlin 2.0.21 / kotlinx-coroutines 1.9.0 probe on JDK 21, using the actual `withContext(Dispatchers.IO)` boundary and a paused caller dispatcher, printed:

```text
committed=true, receivedCancellation=true, receivedSuccess=false
```

The probe marks the point after a successful commit with an atomic boolean; it does not emulate or execute Room. This verifies the cancellation/result-delivery mechanism, while the repository-level scenario above still needs an Android test. The locally cached coroutine source (`commonMain/Builders.common.kt:124-137`) explicitly describes discarding the result when cancellation precedes dispatch back to the original context.

**Suggested direction:** define rollback precisely: exceptions/cancellation observed before successful commit roll back; cancellation while returning a committed result means the caller must reconcile the original operation ID. Retain that ID across lifecycle cancellation and use `getReceipt` or same-ID replay before presenting a definitive failure or starting a replacement operation. Add a repository-level late-cancellation test separately from an in-transaction cancellation test. Simply wrapping everything in `NonCancellable` is not a substitute for an explicit outcome/recovery contract.

The existing cancellation test throws `CancellationException` directly inside `database.withTransaction`. It is useful evidence for rollback before commit, but it neither calls `repository.pay` nor exercises this return-dispatch window.

## Areas inspected without another confirmed defect

These conclusions describe source logic, not executed Room test results.

| Area | Assessment and relevant evidence |
| --- | --- |
| Atomic balance, ledger, receipt, reload, pass, claim, alert | `mutate` puts the complete mutation in one Room transaction; `commit` writes the balance and dependent records within it. Low-balance messages and subsidy claims remain inside the surrounding transaction. The new failure-injection tests target ledger, alert, and pass insertion failures. |
| Double submit and overspending | Operation lookup, balance validation, and writes happen in the same transaction. The application container supplies one repository/database. Operation primary keys and the unique `(operationId,type)` index provide additional constraints. No check-then-write race outside the transaction was found. |
| Same ID with different arguments | Length-prefixed fingerprints include operation kind, card, merchant/amount as applicable, and subsidy month. Replay is checked before current card/frozen/balance rules, preserving the original receipt after later state changes. No ambiguous delimiter collision was found. |
| Failed writes | Rule exceptions and storage exceptions escape the transaction before being mapped to errors. No catch inside the transaction hides a failed ledger/pass/claim write. Cancellation is rethrown; D2 limits what that implies after commit. |
| Seed once | Preferences are the persisted initialization marker; catalog, cards, full seed ledger, operations, preferences, and welcome message are inserted within one transaction. The in-memory mutex/flag is only an optimization. Normal reopen does not replenish balances. Seed debits have preceding funding, and every seed balance update has matching ledger entries. |
| JSON / invalid Room state | No JSON persistence/parser exists in the reviewed data/domain files. Money and preference limits are validated at mutation boundaries, and credit/debit rules reject an invalid stored card balance. Room has card/pass/claim/offer foreign keys and claim/operation uniqueness constraints. Direct SQL corruption is not an exposed user path; this review does not label absent recovery from arbitrary database tampering as a confirmed vulnerability. |
| Offer races and refresh | Read, expiry/redeemed checks, and update occur within `mutate`; favorite changes also reread inside the transaction. `INSERT IGNORE` catalog refresh preserves expiry, favorite, and redeemed fields. No double-redemption or lost-redemption race was found through repository methods. |
| Subsidy | Completed-month validation and analytics both use Hong Kong time. Bounds are inclusive start/exclusive end; only debit types count; only transport category counts toward the claim; integer division floors cents; the amount caps at 10,000 cents. Claim lookup and credit are transactional, with a unique card/month index. |
| Pass expiry | `plusMonths(1)` is evaluated in Hong Kong time; `isActive` and the SQL active-pass query both use inclusive start/exclusive end. This matches the calendar-month contract, including shorter-month adjustment. |
| Reminders | IDs incorporate the target and expiry; `INSERT IGNORE` deduplicates within a transaction. Favorite/unredeemed checks and reminder preferences are applied. |

## Observable-state and test limitations for iteration

- **Cross-flow snapshots are not atomic.** `RoomTapLinkRepository.kt:30-39` exposes independent queries. A consumer combining `cards`, `transactions`, `passes`, or `preferences` can temporarily pair a new value from one collector with an old value from another even though the database commit is atomic. `selectedCard` itself uses a single join and avoids a cards/preferences join in application code. No combined wallet snapshot or revision is exposed. This is an API limitation to document/test; it is not evidence of partial persistence. If a screen requires an internally consistent aggregate, give that aggregate a transactional snapshot/query rather than assuming independently collected flows update simultaneously.
- **Preference replacement needs caller coordination.** `updatePreferences` writes a complete caller-supplied model (`RoomTapLinkRepository.kt:78-83`). A stale snapshot can overwrite a later card selection or another setting. For example: read preferences with card A, select B, then submit the old snapshot with only its theme changed; card A is selected again. That follows the current whole-model API, so it is not elevated to a contract violation here. UI/platform writers should avoid presenting this as a field-wise atomic update; consider a repository-side patch API if multiple writers are intended.
- **Tests arrived during review.** Domain test sources and `RoomTapLinkRepositoryTest.kt` were read, but no Gradle, JUnit, instrumentation, emulator, or app execution was performed by this reviewer. Test existence is not a passing result. The small SQL and coroutine probes above are the only executed checks.
- **Remaining useful Android cases:** cancellation through the repository before/after commit; concurrent initialization from separate repository instances on the same database; parallel redeem/favorite/refresh; parallel subsidy claims; pass repurchase exactly at expiry; Jan-31/leap-day pass expiry; subsidy claim eligibility exactly at Hong Kong month rollover; failure on operation/claim insertion; and observation ordering with deliberately delayed collectors. Existing tests cover several neighboring cases, so extend those tests instead of duplicating implementation.
- **Corruption behavior is unverified.** `preferences` uses `checkNotNull`; receipt reconstruction also requires referenced rows. Removing the preferences marker while leaving seed cards can make reseeding fail on duplicate primary keys. This is not reachable through current public mutation methods; define an explicit recovery policy if import/migration/manual reset is introduced.

## Snapshot and handoff

The source was uncommitted and other authors were active. The findings refer to these SHA-256 snapshots:

```text
API_CONTRACT.md
8F9DA1B902215C02FDF01FDE28BCA78606D7ADBF53EEF706D07CFAA38A9CD014
app/src/main/java/com/taplink/app/data/RoomTapLinkRepository.kt
063BD11F91BBBE75FD92110ABBFF96E1C480C3D9473313BC5E029940FA122B03
app/src/main/java/com/taplink/app/data/TapLinkDao.kt
227BD542094B51471F6909056DEA664366DA7F8F8E858E1A61B7A017242377E3
app/src/androidTest/java/com/taplink/app/data/RoomTapLinkRepositoryTest.kt
E5D22786090C5BFB65B03447A2263EE1D66F42D199D438246332FC1351254550
```

Immediate main-chat delivery of D1 was attempted through `send_message_to_thread`; the tool rejected it because the destination is this review's native ancestor, and this session exposes no native collaboration send tool. Main should pick up D1/D2 from this report/final handoff and assign changes to the existing owners. No fixes were applied by this reviewer.
