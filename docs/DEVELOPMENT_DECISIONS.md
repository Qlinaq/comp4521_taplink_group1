# Implementation decisions

## Scope

TapLink is an English-language Android educational prototype inspired by integrated stored-value card workflows. It uses its own identity and demo data. The interface labels simulated balances, funding, payments, coupons and transport services. It does not connect to Octopus, FPS, banks or real ticketing systems.

## Correctness before feature breadth

- Amounts use integer HKD cents. Parsing rejects invalid precision and out-of-range amounts.
- A successful money operation updates the card and all related ledger entries in one Room transaction.
- A rejected operation leaves the balance and ledger unchanged.
- Confirmation attempts retain an operation ID so retries cannot duplicate a payment.
- Automatic reload follows an affordable payment and runs at most once; it does not rescue an initially unaffordable purchase.
- Spending totals count debit entries and exclude top-ups/reloads/subsidy credits.
- Demo data is initialized once; refresh and restart preserve user changes.

Ledger rows have a persisted insertion sequence, so equal timestamps cannot put a payment ahead of its subsequent reload. Settings updates apply a transformation to the latest stored preferences inside the transaction; changing visibility must not restore an older selected card.

Cancellation before a successful commit rolls back that transaction. Cancellation while a committed result is being delivered is an uncertain outcome, not proof of failure. The original operation ID must be retained and reconciled through its stored receipt or a same-ID retry. A new ID represents a new intended transaction.

## Android behavior

Room is the local source of truth. Core wallet workflows work without network connectivity. UI state must survive rotation; hardware and permission-dependent features have manual alternatives. Notifications remain available in the in-app inbox when system delivery is unavailable. Background reminders are deferrable and do not promise exact timing.

## Team handoff

The code is split into domain, data, UI and Android platform packages. Contributors should keep transaction rules in the repository/domain rather than reproducing balance mutations inside screens. Work on a branch, include reproduction steps for bug fixes, run the relevant tests, and update the feature and verification records when behavior changes.

The supplied dependency versions match the course's working Android stack. Newer releases are not required for this prototype; upgrades should be deliberate and tested rather than part of UI editing.
