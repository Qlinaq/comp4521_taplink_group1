# TapLink: Final Project Report

Hong Kong Public Electronic Payment and Stored Value System-TapLink

COMP4521 Mobile Application Development | Fall 2026

Group number and complete member list: to be confirmed in TEAM.json. Known member: WU, Ziyao (20945294).

Draft for team review. Complete the official cover page and actual contribution statement before submission.

## 1. Background and objectives

TapLink investigates how an Android application can combine stored-value card management, everyday payment workflows and spending information in one clear experience. The target users are students and demonstration participants who want to check a selected card quickly, understand where their spending goes, and find useful service information.

The project takes inspiration from integrated card and transport applications while using its own name, interface and fictional data. It is an educational prototype: balances, funding sources, purchases, passes and coupons are simulated. It does not read real stored-value cards, link bank accounts, connect to Octopus or FPS, or issue real tickets.

The core objectives are persistent management of at least three demo cards; consistent top-up and payment operations; searchable transaction history and accurate spending summaries; and a usable Android interface that handles empty states, invalid inputs and rotation. Supporting features demonstrate reminders, nearby discovery and selected device capabilities. Core transaction correctness takes priority over optional feature breadth.

Two extensions were added after the core wallet had been verified. The first is a local account layer: creating an account with an email address or a Hong Kong phone number, verifying it with a short code, signing in, resetting and changing the password, deleting the account, and gating the wallet behind a session while still allowing a guest to try the demonstration. The second is a Settings language switch for Traditional Chinese, Simplified Chinese and English that applies without restarting the application. Both were scoped so that the verified money paths and their tests remain untouched, and both are described with their limits in section 6 rather than being presented as production capabilities.

## 2. Requirements and design decisions

The proposal's Must/Should/Could priorities were retained. A user can choose a card, review its balance and status, add simulated funds, select a merchant, review a purchase and inspect a receipt. Frozen cards and unaffordable purchases must be rejected. Cancelling a review must not move money. Any successful balance change must have matching stored records.

Local persistence was chosen because the demonstration should remain usable without an external payment service or network connection. Room stores wallet state; screens observe that state instead of maintaining independent copies of balances. This reduces the chance that a displayed balance and transaction list disagree after a restart.

Amounts are represented as integer HKD cents. Parsing accepts ordinary positive decimal amounts with at most two decimal places, avoiding floating-point rounding in financial examples. Validation also limits the maximum stored balance and rejects invalid preferences or overflow before a transaction can commit.

Automatic reload follows the clarified workflow from the proposal diagrams. The original payment must be affordable. After its debit, a balance strictly below the configured threshold can trigger one simulated reload. The payment, reload and final balance are saved together. Reload does not make an initially unaffordable payment valid.

For example, a card with HK$100.00 paying HK$30.00 reaches HK$70.00. With an HK$80.00 threshold and HK$50.00 reload amount, the final balance becomes HK$120.00. Spending remains HK$30.00; the reload is a credit, not expenditure.

## 3. Architecture and implementation

The application uses Kotlin, Jetpack Compose and Room. The code is divided into domain, data, UI and Android platform packages. Domain models define cards, transactions, preferences, merchants, messages, passes, offers, accounts and verification codes. Pure rules handle amount validation, spending calculations and the account policies (identifier normalisation, password strength, code lifetime, lockout escalation, session lifetime). The repository contract is documented separately so screens and device features can evolve without duplicating persistence logic.

Compose screens communicate through a ViewModel, which holds navigation, form and confirmation state. The ViewModel calls repository operations and presents success or error results. Saved state retains relevant input across activity recreation. The data layer implements the repository with Room, asynchronous database access and observable flows. Platform helpers handle notifications, background work, location, widgets and sensors at the Android boundary.

Accounts form a gate in front of that wallet rather than a rewrite of it. A root composable observes a session token and an explicit guest flag; while neither is present it renders the account screens, and otherwise it renders the unchanged wallet shell. Two tables were added: `accounts`, storing a display name, an optional unique email and an optional unique phone number, verification flags, a PBKDF2 salt and hash with its iteration count, and lockout counters; and `auth_codes`, storing a salted SHA-256 digest of a six-digit code with its purpose, expiry, attempts left and consumption time. Passwords are hashed with PBKDF2-HMAC-SHA256 at 210,000 iterations and compared in constant time; the plaintext is never stored or logged, and the instrumentation suite asserts this by scanning every column of the stored row. Sign-in errors deliberately do not reveal whether an identifier exists, and repeated failures lock the identifier with a doubling delay.

Two deliberate scope decisions follow from the coursework setting. The wallet keeps **one local profile**: accounts are an identity check, not a data boundary, so no ledger row carries an account id and the behaviour verified for F01–F18 is unchanged. And the verification code is **delivered through a simulated in-app inbox** rather than email or SMS, because provider credentials in a student repository would be both insecure and liable to expire before marking. The workflow, its policy and its storage are real; only the transport is simulated, and `VerificationSender` isolates it behind one interface so a real transport is a single implementation.


Money operations run within a database transaction. The repository validates the selected card and operation, calculates the new balance, inserts the ledger records and records the receipt outcome in the same atomic unit. If validation or storage fails, the operation should not leave a partial debit or incomplete history. The verification record distinguishes the checks actually executed from the intended guarantees.

A confirmation attempt receives a stable operation ID. Repeating the same request returns its original outcome rather than moving money again. Reusing that ID with different inputs is rejected. The UI also disables concurrent submissions while an operation is in progress, but repository-level protection remains necessary because UI controls alone cannot guarantee correctness.

Seed data provides three named cards and a ledger of fictional activity. Initialization occurs once; ordinary restart and content refresh preserve user changes. Seeded balances are derived from their corresponding activity instead of being unrelated display values. New cards start with zero balance and can be funded through the same top-up workflow used elsewhere.

## 4. User experience and feature workflows

The dashboard emphasizes the selected card and common actions. Users can switch, create and rename cards, hide the balance, and freeze or unfreeze a card through confirmation. Recent activity provides a direct route to transaction details. The interface consistently identifies simulated services and uses concise validation messages.

Top-up and payment forms lead to a separate review step before confirmation. The resulting receipt identifies the operation, amount and resulting balance. A related automatic reload is shown explicitly so the user can understand why the final balance differs from a simple debit. Transaction details can be revisited from history.

History supports card, type, category and date filtering together with merchant-name search. Spending analysis excludes credits and derives totals from applicable debit records. A custom Android View draws the category chart using Canvas, demonstrating a course technique alongside the Compose interface. Empty filter results receive a clear message rather than an unexplained blank screen.

Nearby discovery uses a seeded catalogue of approximate Hong Kong service locations. It offers a manual district path so permission denial does not prevent browsing. Locations remain demonstration data rather than a live directory. An external map intent can open the approximate location in an available map application.

The message centre preserves alerts inside the application. System notifications are an optional delivery surface, not the sole record of an important message. Background reminders and content refresh use persistent, deferrable scheduling; exact execution times are not promised. Widget balance visibility is separately controlled because a home-screen widget can expose information outside the app. The optional shake gesture only changes visibility and never triggers a payment.

Transport and reward demonstrations extend the same principles. A simulated pass uses the existing payment rules and has visible validity dates. Offer favorites and redemption state persist, with repeated or expired redemption rejected. Any subsidy example uses a documented fictional rule and cannot be interpreted as an official entitlement or claim.

Account handling is presented as a short, linear flow so it can be followed by a first-time user. The welcome screen offers sign-in, sign-up, or continuing as a guest without an account. Sign-up takes a display name, either an email address or a Hong Kong phone number, and a password, then asks for the six-digit code; the code appears in a panel explicitly labelled as simulated, together with the masked destination address. Resetting a password follows the same two steps, and a successful reset ends the previous session. Settings shows the signed-in identity with a masked identifier and offers changing the password, signing out and deleting the account; deletion requires the current password. Validation is expressed as short field-level sentences rather than as a generic failure, and every failure keeps the user on the screen with the entered values intact.

The interface is available in English, Traditional Chinese and Simplified Chinese, selected in Settings and remembered across launches. Localisation covers the wallet, the account flow, the custom chart, the accessibility descriptions and the notification and error text, not only the navigation labels. Dates, month headings and category names follow the chosen language, and the switch applies immediately without restarting the activity. A guard test compares the three resource files and fails the build if a key is missing, a value is blank or a format placeholder is lost, which prevents the most visible form of regression in a demonstration.

Because the brief framed TapLink as a public-service application, the final round was designed around
obligations rather than features. Notifications became configurable rather than merely present: a
master switch, five independent categories and the live Android permission state, with the reason
for each spelled out. The governing rule is that **an alert is stored before it is posted**, so a
resident who denies the notification permission still finds every message in the inbox — the
permission removes the interruption, not the record.

Accessibility is treated as four coordinated options rather than one switch. Elderly-friendly mode
turns on 150% text, a high-contrast palette, read-aloud and a simplified home page together, and
turns them off together; each option can then be adjusted alone. Large text scales the whole
interface, which exposed genuine layout work rather than a font-size change: at 150% the activity
row moves its amount under the merchant instead of squeezing three columns into one line, and the
support screen gives up its page heading so the conversation keeps the space. Read-aloud speaks a
curated sentence per screen — the balance and the next action — instead of reading every label, and
reports honestly when no Chinese speech engine is installed.

The contact and support surface keeps the same discipline. WhatsApp opens with a draft the user must
still send, the hotline opens only the dialer, and the screen states plainly that TapLink is a
coursework prototype rather than an Octopus or government service. The text assistant answers with a
cheap model under a visible daily quota, is instructed never to request a secret and to hand
account-specific matters to a human, refuses password-shaped input before it leaves the device, and
falls back to a local FAQ when the network or the quota is gone. Sign-language support ships a real
player with an honest gap: the chapters carry complete written transcripts and the video slot
enables itself only when a clip is added, because a placeholder presented as sign language would
mislead exactly the users the feature exists for.

## 5. Verification and review

The acceptance plan covers normal operation and failure paths. Important cases include invalid precision, zero and negative amounts, frozen cards, insufficient balances, duplicate confirmation, concurrent spending, reload threshold boundaries, persistence and filtered totals. Device-focused checks cover rotation, navigation, permission denial and the ability to continue using local data.

Pure unit tests target calculations and policy boundaries. Instrumentation tests exercise the Room repository in Android. Installed-app checks confirm that the visible workflow reaches the expected stored outcome. The tested APK is identified by its hash so the delivered binary can be compared with the verified build.

Independent AI-assisted review is used to identify concrete defects rather than only comment on style. Findings are recorded with reproduction conditions and addressed through code changes and relevant regression checks. The verified implementation passed 94 JVM tests and 49 Android instrumentation tests on an emulator running API 35. Android lint reported zero errors. The installed UI demonstration produced exactly three matching ledger entries: a HK$100 top-up, HK$30 payment and HK$50 automatic reload, leaving HK$120. The filtered spending chart correctly reported HK$30. Rotation retained payment details, restart retained the card and balance, and permission denial retained manual nearby browsing. A read-only SQLite integrity check returned ok. The account flow was exercised on the installed application as well: sign-up produced a six-digit simulated code, verification returned to Settings with the masked identifier, changing the password succeeded, signing out returned to the welcome screen, the old password was rejected and the new one accepted. A database dump after sign-up contained the hash and no plaintext password. Traditional and Simplified Chinese were each applied on the device without a restart, and the navigation labels, dates and category names changed with the content. The accompanying VERIFICATION.md identifies the build, records actual evidence and distinguishes these checks from untested physical hardware and long-duration scheduling behavior.

## 6. Limitations and future work

The prototype operates on simulated local data. It provides local account authentication — sign-up, sign-in by email or Hong Kong phone number, verification, password reset, password change, account deletion and a session gate — but it is not a production identity service. The wallet keeps a single local profile, so an account is an identity check rather than a data boundary, and signing in as a different person on the same installation shows the same wallet. Verification codes are shown in a simulated in-app inbox instead of being sent by email or SMS, because provider credentials must not live in a course repository. Sessions are stored in ordinary preferences rather than the Android Keystore, there is no server, and consequently there is no remote recovery, device revocation, rate limiting at the edge or cross-device synchronization. There is no financial-service security: balances, merchants, offers and transport rules remain controlled examples, merchant QR scanning is omitted in favour of manual selection, and the embedded map is a labelled overview of seed locations. Physical shake sensitivity, real launcher widget placement, full OS notification delivery combinations and long-duration background scheduling have not been certified. The support assistant is a demonstration compromise rather than a deployable design: its API key is compiled into the APK from a git-ignored properties file, so it must be treated as public, its daily quota is enforced locally per installation, and it must be replaced by a server proxy before any release. No sign-language footage is included, read-aloud depends on a Chinese speech engine being present, and the accessibility work targets the Hong Kong Government WCAG 2.1 AA baseline without constituting a certified audit or an exhaustive screen-reader pass. Hardware-dependent behavior can vary between an emulator and a physical phone; only the environments listed in the verification record should be claimed as tested.

Future work could add a properly designed synchronization service in which accounts partition wallet data, server-side password hashing and token handling, broader accessibility testing, more realistic catalogue updates and evaluation with classmates. Such changes would require new conflict-resolution, privacy and reliability decisions. They should not weaken the existing atomic transaction rules or make the core demonstration depend on an unreliable network.

## 7. Team contributions and generative AI

The complete member list and actual contributions remain to be confirmed by the group. Each member should record the code they reviewed or modified, tests they performed, documentation they revised and sections they present. No contribution is assigned merely because a name appears on the cover page.

OpenAI Codex assisted with architecture guidance, code generation and refinement, documentation, test development, debugging and independent review. AI assistance was also used for proposal diagram generation and refinement. This was substantial implementation assistance, not only language polishing. The group should review the final implementation, verify the reported results and update the acknowledgement for any further tools used before submission.

## References

Course specification, final submission instructions and rubric: linked in COURSE_REQUIREMENTS.md.

Android Developers: Room asynchronous queries and transaction APIs; WorkManager persistent and periodic work documentation.

GitHub Docs: Building and testing Java with Gradle. Full reference links and provenance are listed in REFERENCES.md.
