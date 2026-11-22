# Acceptance test plan

Expected outcomes below derive from the proposal and implementation contract. Execution results belong in the verification record; the existence of this plan is not evidence of a pass.

| ID | Scenario | Expected result |
| --- | --- | --- |
| C01 | First launch | At least three demo cards and consistent seeded records appear. |
| C02 | Create and rename card | A zero-balance card is created; its new name persists after restart. |
| C03 | Select a different card | Dashboard and subsequent transactions use the chosen card; selection persists. |
| C04 | Freeze and unfreeze | Confirmation is required in UI; frozen card rejects top-ups/payments; unfreezing restores use. |
| T01 | Top up HK$100 | Balance increases by 10,000 cents and one matching credit record is saved. |
| T02 | Positive decimal parsing | HK$0.01, HK$1.20 and ordinary whole amounts are exact. |
| T03 | Invalid money | Blank, zero, negative, exponent notation, excessive precision and overflow are rejected. |
| T04 | Affordable merchant payment | Exact debit, immutable transaction details and receipt are stored together. |
| T05 | Insufficient balance | No card, ledger or message side effects. Automatic reload does not rescue it. |
| T06 | Same request repeated | Returns the original receipt without another debit/reload/message. |
| T07 | Same ID, different request | Clear conflict error with no state change. |
| T08 | Parallel payments | Serialized transactions cannot overdraw a card or lose a balance update. |
| T09 | Storage failure/cancellation | No partially committed balance/ledger state. |
| T10 | Automatic reload | At most one reload after an affordable debit below threshold; commits with the debit. |
| T11 | Exact threshold boundary | A balance equal to the threshold does not reload. |
| T12 | Reload exceeds limit | The entire operation is rejected without a partial debit. |
| T13 | Persistence | Reopening the app/database retains balances, records and settings without reseeding. |
| H01 | Filters and merchant search | Card/type/category/date/query conditions all apply together. |
| H02 | Inclusive start, exclusive end | Records exactly at the start are included; exactly at the end are excluded. |
| H03 | Spending totals and chart | Sum only payment/pass debits; exclude every type of credit. |
| H04 | Empty result | Clear empty state; filters can be changed without a crash. |
| U01 | Rotate form and review | Card, amount, merchant and confirmation state remain usable. |
| U02 | Back and double-tap confirm | No accidental second payment or return to a completed confirmation. |
| U03 | Light/dark and long content | Readable controls, scrollable layouts and no obscured primary actions. |
| P01 | Notifications denied | Core wallet works and alert remains in the in-app inbox. |
| P02 | Background preferences | Disabled reminders/refresh do not continue the corresponding work. |
| P03 | Approximate location/denial | Nearby feature accepts coarse location and offers manual district fallback. |
| P04 | Widget privacy | Balance hidden by default; explicit setting controls exposure. |
| P05 | Shake gesture | Only changes dashboard balance visibility; no payment and no listener off-screen. |
| E01 | Pass purchase and repeat | One paid pass with valid dates; active duplicate is rejected without charge. |
| E02 | Coupon favorite/redemption | Favorite persists; expired or already redeemed coupons cannot be redeemed again. |
| E03 | Fictional subsidy | Completed-month rule and cap are exact; duplicate claim does not credit again. |
| E04 | Demo catalogue refresh | Preserves existing card balances, preferences, favorites and redemptions. |

Tests should use dedicated test databases or the TapLink demo installation, not unrelated user apps. Record the APK hash and tested source revision when preparing a handoff.
