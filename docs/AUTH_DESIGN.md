# Accounts, sessions and interface language

Design record for the two features added after the F01–F18 wallet: a local account layer with
sign-up, sign-in, password reset, password change, account deletion and session gating (F19), and a
three-language interface switch in Settings (F20).

This document states what was built, what it deliberately does not do, and what a production
version would have to add. It is written to be defended in a demonstration: every claim below can
be reproduced on the delivered APK.

## 1. Scope chosen for a graded coursework prototype

The request was: an account system that supports sign-in, password reset and signing in with an
email address or a phone number, plus a Settings language switch for Traditional and Simplified
Chinese.

Three scopes were considered.

| Option | Content | Effect on the existing wallet |
| --- | --- | --- |
| A — local demo only | Static screens, no storage, no rules | Nothing changes, but nothing can be demonstrated either |
| **B — lightweight identity gate (chosen)** | Account table, every screen, session gating, change/reset password, hashed credentials, lockout, OTP policy | The wallet keeps one local profile; no existing query or instrumentation test changes |
| C — multi-profile | Per-account partitioning of cards, transactions, receipts, passes, offers, messages; migration of seeded data | Every repository query and all 29 existing instrumentation cases would need a profile predicate |

Option B was chosen because it can be completed and verified inside the course deadline without
destabilising the money paths that the rest of the project depends on. Option C is described in
section 9 as future work, not as something that is half-present.

Delivery is **simulated**: the six-digit code appears in an in-app "demo inbox" instead of being
sent by SMTP or SMS. Section 7 explains why that is a defensible engineering decision and not a
shortcut hidden from the marker.

## 2. Architecture

```
MainActivity
  └─ TapLinkRoot(repository, authRepository, platform)
       ├─ AuthViewModel ── RoomAuthRepository ─┬─ Room: accounts, auth_codes
       │                                       └─ SessionStore (SharedPreferences)
       ├─ LocalizedContent(preferences.language)
       └─ if (session != null || guest)  TapLinkApp(...)   // the F01–F18 wallet, unchanged
          else                          AuthFlow(authVm)   // welcome, sign in/up, verify, reset
```

Two independent pieces of state decide what the user sees:

- **session** — an opaque token in `SharedPreferences`, validated against `accounts` on start.
- **guest** — an explicit "continue without an account" flag stored next to the session.

Neither is derived from the wallet. The wallet's own tables are never filtered by account, which is
the single-profile decision described in section 8.

`TapLinkApp` received no changes beyond localisation; the gate is a wrapper. That is what keeps the
29 pre-existing instrumentation tests valid.

## 3. Data model

`accounts` (`AccountEntity`)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | UUID |
| `displayName` | TEXT | 1–40 characters after trimming |
| `email`, `phone` | TEXT? UNIQUE | At least one is present; the other stays NULL |
| `emailVerified`, `phoneVerified` | INTEGER | Set when the code for that channel is consumed |
| `passwordSalt`, `passwordHash` | TEXT | Base64; the hash is PBKDF2 output, never the password |
| `passwordIterations` | INTEGER | Stored per row so the cost can be raised later without a migration |
| `failedAttempts`, `lockUntil` | INTEGER | Lockout escalation state |
| `createdAt`, `updatedAt` | INTEGER | Epoch millis |

`auth_codes` (`AuthCodeEntity`)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | UUID |
| `identifier` | TEXT | Normalised email or E.164 phone |
| `channel`, `purpose` | TEXT | `EMAIL`/`PHONE` × `SIGN_UP`/`RESET` |
| `codeSalt`, `codeDigest` | TEXT | Salted SHA-256 of the six digits; the code itself is not stored |
| `createdAt`, `expiresAt` | INTEGER | 300 s lifetime |
| `attemptsLeft` | INTEGER | Starts at 3 |
| `consumedAt` | INTEGER? | Set on success or when superseded |

Two details that are easy to get wrong:

- **Nullable unique indices.** `email` and `phone` are both `UNIQUE`, but SQLite treats `NULL` as
  distinct, so any number of phone-only accounts may leave `email` NULL. A single `identifier`
  column with a separate `channel` column would have forced one index and a uniqueness bug.
- **Enums are stored by name.** `channel` and `purpose` are persisted as `TEXT` through the enum
  name, so a future reordering of the Kotlin enum cannot silently reinterpret stored rows.

## 4. Credential handling

| Concern | Implementation |
| --- | --- |
| Password hashing | PBKDF2-HMAC-SHA256, 210,000 iterations, 16-byte random salt, 256-bit derived key |
| Verification | `MessageDigest.isEqual` on the derived key — constant time, no early return |
| Per-row cost | Iteration count stored with the row, so `DEFAULT_ITERATIONS` can increase for new rows only |
| One-time codes | Salted SHA-256 digest; the six digits exist only in memory until the demo inbox renders them |
| Session token | 256-bit `SecureRandom`, opaque, mapped to an account id; 30-day lifetime |
| Session storage | `SharedPreferences`, cleared on expiry or sign-out |

The password is never logged, never written to the database, and never placed in a `SavedStateHandle`.
The instrumentation suite asserts this the blunt way: it dumps every column of the account row after
sign-up and fails if the plaintext password appears anywhere in it.

## 5. Verification code policy

| Rule | Value | Reason |
| --- | --- | --- |
| Length | 6 digits | Familiar, and short enough to type on a demo device |
| Lifetime | 300 s | Long enough to read from the demo inbox, short enough to test expiry |
| Attempts | 3 | Limits online guessing of a 10⁶ space |
| Resend cooldown | 60 s | Prevents a tap storm from generating unlimited codes |
| Sends per hour | 5 | Caps abuse of the issuing path per identifier |
| Superseding | A new code invalidates the previous one | Prevents two valid codes for one account |
| Reuse | `consumedAt` is set on success | A code cannot verify twice |

## 6. Lockout

Five failed passwords lock the identifier for 60 s; each further failure doubles the delay up to a
300 s ceiling, and a success clears the counter. The lock is evaluated inside the same transaction as
the password check, so two parallel guesses cannot both observe the pre-lock state.

Login errors are deliberately **non-enumerable**: an unknown identifier and a wrong password both
return `WRONG_CREDENTIALS`. Sign-up is the one place that distinguishes "identifier already taken",
because the user has to be told to sign in instead.

## 7. Why the code delivery is simulated

Sending real email or SMS would require provider credentials inside a student repository. Any key
committed to a course repository is compromised the moment it is pushed, and a provider account can
also expire between the demonstration and the marking. Both failure modes are worse than a visibly
labelled simulation:

- The screen says **Simulated** and shows the code in a "demo inbox" panel.
- `VerificationSender` is an interface. `SimulatedVerificationSender` keeps the last delivery in
  memory only — it is never persisted and never logged.
- Replacing it with a real transport is a single implementation of `send(delivery)`, with no change
  to the policy, storage or screens.

The honest statement for the report is therefore: *the verification workflow, its policy and its
storage are real; the transport is simulated.*

## 8. Wallet ownership: one wallet per account

The first version of the session gate deliberately shared one wallet. A device check showed why that
is the wrong answer for a stored-value app: signing in as another account still showed the previous
balance. Database version 5 therefore gives every wallet row an owner.

**What an owner is.** `WalletOwner.GUEST` is the demo wallet used without an account; every signed-in
account uses its own account id. `cards`, `transactions`, `operations`, `messages`, `passes` and
`subsidy_claims` carry `ownerId`, and every wallet query takes it as a parameter. Two owners cannot
read each other's rows, and a card id from another owner fails with `NOT_FOUND` rather than being
spendable. `merchants`, `locations` and `offers` stay a shared demo catalogue because they are
content, not money.

**How the switch happens.** `WalletViewModel` takes the owner as a constructor argument, calls
`repository.useOwner(ownerId)` before any flow is collected, and `TapLinkApp` is wrapped in
`key(owner)`, so a new account gets a new wallet shell with no saved route, scroll position or cached
card. The repository keeps the owner in a `StateFlow`, so a flow that is already being collected — the
widget, a worker — switches instead of staying on the previous wallet. Background work resolves the
owner from the stored session (`TapLinkContainer.activeOwner`) rather than assuming the demo wallet.

**What a new account starts with.** One empty card (HK$0.00) and a welcome message, not a copy of the
three-card demo wallet. Copying the demo numbers would make two accounts look identical, and a
stored-value wallet that appears pre-funded would be a false claim. A new account's first message
says the wallet is empty and everything is simulated.

**The upgrade path.** `MIGRATION_4_5` marks existing rows as the demo wallet. On the first launch
after the update, a signed-in account adopts those rows once and records a marker in `app_meta`, so an
update does not look like data loss and a later account never inherits them. A fresh database has no
rows at that moment; its demo wallet is seeded immediately afterwards and stays separate.

Consequences, stated plainly:

- Signing in as a different person now shows that person's cards, balance and history.
- Deleting an account removes the credential row and ends the session; the wallet rows are kept, so
  the same account signing up again with the same identifier does not find them.
- The boundary is enforced in the app's own queries, not by the operating system: a rooted device can
  still read the database file. It prevents a wrong wallet appearing on screen, not a forensic
  attacker.
- The demo wallet and an account are deliberately separate: browsing as a guest and then creating an
  account does not carry the demo balance over.

## 9. Production replacement path

| Prototype | Production |
| --- | --- |
| PBKDF2 in the app, salt and hash in Room | Server-side Argon2id or scrypt; client sends the password over TLS only |
| `SessionStore` in `SharedPreferences` | Token in `EncryptedSharedPreferences` or the Android Keystore, short-lived access token plus refresh token |
| Simulated sender | Backend calls an email/SMS provider; the client never holds provider credentials |
| 30-day local session | Server-side revocation, device list, refresh rotation |
| `ownerId` per wallet row in one local database | Server-side ownership with a foreign key to the account and row-level authorisation on every request |
| Lockout counters in the app | Rate limiting at the edge, per identifier and per IP |

Encrypting the local database (SQLCipher) and moving the hash to the Keystore are the two changes
that would matter most if this prototype were ever pointed at real data.

## 10. Interface language

The requirement was a Settings switch for Traditional Chinese (`zh-TW`), Simplified Chinese
(`zh-CN`) and English, with the choice persisted.

| Decision | Reason |
| --- | --- |
| `createConfigurationContext` + `CompositionLocalProvider` instead of activity recreation | Instant switch, navigation state survives, and `minSdk 29` rules out the API 33 `LocaleManager` while the project has no AppCompat dependency |
| `LocalAppLocale` composition local for dates | A plain `Locale.setDefault` is not observable state: Compose legitimately skipped already-composed items (the month header, transaction dates) and left them in the previous language. Reading a composition local makes the dependency explicit and they redraw |
| Locale-aware date patterns | `d MMM yyyy` under `zh-TW` renders "15 11月 2026", which reads like a bug. Chinese locales use `yyyy年M月d日` and `yyyy年M月` |
| Enum labels resolved through resources | A category label is interface text, not user data, so it follows the language rather than the wording it was created with |
| Operational messages mapped by their exact English text | `WalletViewModel` keeps stable strings so they survive the saved-state round trip; `OperationalMessages.kt` maps each to a resource. A production refactor would emit a resource id directly, and that change is mechanical |
| `taplink_widget_brand` marked `translatable="false"` | The product name is not translated |
| `LocalizationParityTest` | Fails the build if any locale differs by one key, has a blank value, or loses a `%1$s` placeholder — the defect a marker would otherwise find on screen |

611 translated keys exist in all three locales, plus one key (`taplink_widget_brand`) marked
`translatable="false"`. Widget labels live in their own `widget_strings.xml` and are covered by the
same test, which reads every `*.xml` in each values directory rather than assuming one file.

## 11. Test inventory

| Suite | Count | Covers |
| --- | ---: | --- |
| `AuthRulesTest` | 17 | Identifier normalisation, masking, password truth table, OTP policy, lockout escalation, session lifetime |
| `PasswordHasherTest` | 12 | Hash/verify, unique salts, cross-salt rejection, malformed Base64, iteration honouring, code digest, token shape |
| `RoomAuthRepositoryTest` | 20 | Real Room: hash-only storage, verification, lockout and expiry, code attempts/expiry/resend, reset, change, phone account, deletion, session expiry |
| `LocalizationParityTest` | 4 | Key parity, no blank values, placeholder parity, untranslated product name |

Totals for the whole project after these additions, and after the later notifications and
accessibility round: **94 JVM tests, 49 instrumentation tests, lint clean (0 errors, 51 advisory
warnings)**.

## 12. Limitations to state in the report

1. The code transport is simulated; no email or SMS leaves the device.
2. One profile per installation; accounts are not a data boundary.
3. Sessions live in plain `SharedPreferences`, not the Keystore.
4. There is no server, so there is no remote recovery, device revocation or cross-device sync.
5. Password reset requires access to the demo inbox, which on a single device is a demonstration
   convenience rather than a security property.
6. Account deletion removes credentials only, by design (section 8).
