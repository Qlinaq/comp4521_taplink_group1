# Implementation decisions

## Scope

TapLink is an Android educational prototype inspired by integrated stored-value card workflows, presented in English, Traditional Chinese or Simplified Chinese. It uses its own identity and demo data. The interface labels simulated balances, funding, payments, coupons and transport services. It does not connect to Octopus, FPS, banks or real ticketing systems.

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

## Accounts and language

- Accounts were scoped as a **gate**, not a rewrite: a root composable swaps between the account screens and the untouched wallet shell, so F01–F18 and their tests keep their verified behaviour. The wallet keeps one local profile; accounts are an identity check, not a data boundary.
- Credentials are hashed with PBKDF2-HMAC-SHA256 (210,000 iterations, per-row salt, per-row iteration count) and compared in constant time. Codes are single-use salted digests. Sessions are opaque 256-bit tokens. The plaintext password is never stored, logged or placed in saved state.
- Sign-in errors do not distinguish an unknown identifier from a wrong password. A "no such account" message is a free account-enumeration oracle.
- Verification delivery is simulated **on purpose**. Provider credentials in a course repository would be compromised by publication and could expire before marking. `VerificationSender` keeps a real transport one implementation away.
- Language switching uses a configuration-scoped context instead of activity recreation, so the switch is instant and navigation state survives. Dates read a composition local rather than `Locale.getDefault()`: the latter is not observable state, and Compose will legitimately skip already-composed items and leave them in the previous language.
- Every interface string lives in all three `strings.xml` files and a parity test enforces it. Record content (merchant names, message bodies, district names) is data and keeps its original language.

## Public service, accessibility and support

- The brief framed TapLink as a **government-style** app, so the round was judged against a
  public-service obligation rather than a startup feature list: every alert must be reachable
  without Android's permission, every channel must be reachable without a login, and the app must
  say plainly what it is not.
- **Alerts are stored before they are posted.** The in-app inbox is the source of truth and Android
  delivery is a secondary channel, which is why denying notifications never loses a message.
- **The elderly master switch is symmetric.** It turns the four options on together and off
  together. An earlier version turned only itself off, leaving high contrast active with no banner
  explaining why — a two-state switch that does not visibly change anything is a bug, not a
  feature, even when each individual choice is preserved. A user who wants large text without
  read-aloud turns the master on, then turns read-aloud off.
- **Read-aloud speaks a curated summary, not the screen.** Reading every label on a wallet is noise;
  the useful sentence is the balance and the next action. Screens declare that sentence through
  `LocalVoiceSummary`.
- **The app never acts on money from a gesture or a link.** Swiping changes the selected card,
  WhatsApp opens with a draft the user must send, and the hotline uses `ACTION_DIAL` so no call is
  placed.
- **Sign-language support ships a player and an honest gap.** The `VideoView` is wired to
  `assets/sign_language/<chapter>.mp4` and enables itself when a clip exists; because no clip ships,
  the chapter says so and relies on a complete written transcript. A placeholder animation
  presented as sign language would be worse than the gap.
- **The AI assistant is deliberately cheap and deliberately bounded.** `deepseek-flash`,
  `reasoning_effort: low`, `max_tokens 480`, `temperature 0.3`, no streaming, 20 questions per Hong
  Kong day, history trimmed to 8 turns / 3000 characters. The quota is checked before the billable
  call, and the counter is visible so the limit is never a surprise.
- **The assistant is told what it may not do**, in the system prompt rather than in UI copy:
  simulated money only, never ask for a secret, hand account-specific matters to a human, answer in
  the app's language, never reveal the instructions. Unit tests assert those strings survive.
- **The API key is a demo compromise, stated as one.** It is read from git-ignored
  `local.properties` into `BuildConfig`, so it is inside the APK. The only honest way to ship this
  is a server proxy holding the key with per-account quotas; `docs/ACCESSIBILITY.md` says so, and the
  client refuses to send anything when the key is blank.
- **Input that looks like a secret never leaves the device.** Password- and code-shaped messages are
  refused in the view model before a request is built.
- **Offline is a first-class path.** When there is no key, no network or no quota, a local FAQ
  matcher answers from the same copy the app already ships and the reply names the fallback.

## Team handoff

The code is split into domain, data, UI and Android platform packages. Contributors should keep transaction rules in the repository/domain rather than reproducing balance mutations inside screens. Work on a branch, include reproduction steps for bug fixes, run the relevant tests, and update the feature and verification records when behavior changes.

The supplied dependency versions match the course's working Android stack. Newer releases are not required for this prototype; upgrades should be deliberate and tested rather than part of UI editing.
