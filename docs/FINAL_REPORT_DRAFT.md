# TapLink: Final Project Report

Hong Kong Public Electronic Payment and Stored Value System-TapLink

COMP4521 Mobile Application Development | Fall 2026

Group number and complete member list: to be confirmed in TEAM.json. Known member: WU, Ziyao (20945294).

Draft for team review. Complete the official cover page and actual contribution statement before submission.

## 1. Background and objectives

TapLink investigates how an Android application can combine stored-value card management, everyday payment workflows and spending information in one clear experience. The target users are students and demonstration participants who want to check a selected card quickly, understand where their spending goes, and find useful service information.

The project takes inspiration from integrated card and transport applications while using its own name, interface and fictional data. It is an educational prototype: balances, funding sources, purchases, passes and coupons are simulated. It does not read real stored-value cards, link bank accounts, connect to Octopus or FPS, or issue real tickets.

The core objectives are persistent management of at least three demo cards; consistent top-up and payment operations; searchable transaction history and accurate spending summaries; and a usable Android interface that handles empty states, invalid inputs and rotation. Supporting features demonstrate reminders, nearby discovery and selected device capabilities. Core transaction correctness takes priority over optional feature breadth.

## 2. Requirements and design decisions

The proposal's Must/Should/Could priorities were retained. A user can choose a card, review its balance and status, add simulated funds, select a merchant, review a purchase and inspect a receipt. Frozen cards and unaffordable purchases must be rejected. Cancelling a review must not move money. Any successful balance change must have matching stored records.

Local persistence was chosen because the demonstration should remain usable without an external payment service or network connection. Room stores wallet state; screens observe that state instead of maintaining independent copies of balances. This reduces the chance that a displayed balance and transaction list disagree after a restart.

Amounts are represented as integer HKD cents. Parsing accepts ordinary positive decimal amounts with at most two decimal places, avoiding floating-point rounding in financial examples. Validation also limits the maximum stored balance and rejects invalid preferences or overflow before a transaction can commit.

Automatic reload follows the clarified workflow from the proposal diagrams. The original payment must be affordable. After its debit, a balance strictly below the configured threshold can trigger one simulated reload. The payment, reload and final balance are saved together. Reload does not make an initially unaffordable payment valid.

For example, a card with HK$100.00 paying HK$30.00 reaches HK$70.00. With an HK$80.00 threshold and HK$50.00 reload amount, the final balance becomes HK$120.00. Spending remains HK$30.00; the reload is a credit, not expenditure.

## 3. Architecture and implementation

The application uses Kotlin, Jetpack Compose and Room. The code is divided into domain, data, UI and Android platform packages. Domain models define cards, transactions, preferences, merchants, messages, passes and offers. Pure rules handle amount validation and spending calculations. The repository contract is documented separately so screens and device features can evolve without duplicating persistence logic.

Compose screens communicate through a ViewModel, which holds navigation, form and confirmation state. The ViewModel calls repository operations and presents success or error results. Saved state retains relevant input across activity recreation. The data layer implements the repository with Room, asynchronous database access and observable flows. Platform helpers handle notifications, background work, location, widgets and sensors at the Android boundary.

Money operations run within a database transaction. The repository validates the selected card and operation, calculates the new balance, inserts the ledger records and records the receipt outcome in the same atomic unit. If validation or storage fails, the operation should not leave a partial debit or incomplete history. The verification record distinguishes the checks actually executed from the intended guarantees.

A confirmation attempt receives a stable operation ID. Repeating the same request returns its original outcome rather than moving money again. Reusing that ID with different inputs is rejected. The UI also disables concurrent submissions while an operation is in progress, but repository-level protection remains necessary because UI controls alone cannot guarantee correctness.

Seed data provides three named cards and a ledger of fictional activity. Initialization occurs once; ordinary restart and content refresh preserve user changes. Seeded balances are derived from their corresponding activity instead of being unrelated display values. New cards start with zero balance and can be funded through the same top-up workflow used elsewhere.

## 4. User experience and feature workflows

The dashboard emphasizes the selected card and common actions. Users can switch, create and rename cards, hide the balance, and freeze or unfreeze a card through confirmation. Recent activity provides a direct route to transaction details. The English interface consistently identifies simulated services and uses concise validation messages.

Top-up and payment forms lead to a separate review step before confirmation. The resulting receipt identifies the operation, amount and resulting balance. A related automatic reload is shown explicitly so the user can understand why the final balance differs from a simple debit. Transaction details can be revisited from history.

History supports card, type, category and date filtering together with merchant-name search. Spending analysis excludes credits and derives totals from applicable debit records. A custom Android View draws the category chart using Canvas, demonstrating a course technique alongside the Compose interface. Empty filter results receive a clear message rather than an unexplained blank screen.

Nearby discovery uses a seeded catalogue of approximate Hong Kong service locations. It offers a manual district path so permission denial does not prevent browsing. Locations remain demonstration data rather than a live directory. An external map intent can open the approximate location in an available map application.

The message centre preserves alerts inside the application. System notifications are an optional delivery surface, not the sole record of an important message. Background reminders and content refresh use persistent, deferrable scheduling; exact execution times are not promised. Widget balance visibility is separately controlled because a home-screen widget can expose information outside the app. The optional shake gesture only changes visibility and never triggers a payment.

Transport and reward demonstrations extend the same principles. A simulated pass uses the existing payment rules and has visible validity dates. Offer favorites and redemption state persist, with repeated or expired redemption rejected. Any subsidy example uses a documented fictional rule and cannot be interpreted as an official entitlement or claim.

## 5. Verification and review

The acceptance plan covers normal operation and failure paths. Important cases include invalid precision, zero and negative amounts, frozen cards, insufficient balances, duplicate confirmation, concurrent spending, reload threshold boundaries, persistence and filtered totals. Device-focused checks cover rotation, navigation, permission denial and the ability to continue using local data.

Pure unit tests target calculations and policy boundaries. Instrumentation tests exercise the Room repository in Android. Installed-app checks confirm that the visible workflow reaches the expected stored outcome. The tested APK is identified by its hash so the delivered binary can be compared with the verified build.

Independent AI-assisted review is used to identify concrete defects rather than only comment on style. Findings are recorded with reproduction conditions and addressed through code changes and relevant regression checks. The verified implementation passed 51 JVM tests and 29 Android instrumentation tests on a Pixel 6 emulator running API 35. Android lint reported zero errors and zero warnings. The installed UI demonstration produced exactly three matching ledger entries: a HK$100 top-up, HK$30 payment and HK$50 automatic reload, leaving HK$120. The filtered spending chart correctly reported HK$30. Rotation retained payment details, restart retained the card and balance, and permission denial retained manual nearby browsing. A read-only SQLite integrity check returned ok. The accompanying VERIFICATION.md identifies the build, records actual evidence and distinguishes these checks from untested physical hardware and long-duration scheduling behavior.

## 6. Limitations and future work

The prototype deliberately operates on simulated local data. It does not provide production authentication, remote account recovery, cross-device synchronization or financial-service security. Nearby locations, merchant offers and transport rules are controlled examples. Merchant QR scanning is omitted; users select merchants manually. The embedded map is a labelled overview of seed locations. Physical shake sensitivity, real launcher widget placement, full OS notification delivery combinations and long-duration background scheduling have not been certified. Hardware-dependent behavior can vary between an emulator and a physical phone; only the environments listed in the verification record should be claimed as tested.

Future work could add a properly designed synchronization service, broader accessibility testing, more realistic catalogue updates and evaluation with classmates. Such changes would require new conflict-resolution, privacy and reliability decisions. They should not weaken the existing atomic transaction rules or make the core demonstration depend on an unreliable network.

## 7. Team contributions and generative AI

The complete member list and actual contributions remain to be confirmed by the group. Each member should record the code they reviewed or modified, tests they performed, documentation they revised and sections they present. No contribution is assigned merely because a name appears on the cover page.

OpenAI Codex assisted with architecture guidance, code generation and refinement, documentation, test development, debugging and independent review. AI assistance was also used for proposal diagram generation and refinement. This was substantial implementation assistance, not only language polishing. The group should review the final implementation, verify the reported results and update the acknowledgement for any further tools used before submission.

## References

Course specification, final submission instructions and rubric: linked in COURSE_REQUIREMENTS.md.

Android Developers: Room asynchronous queries and transaction APIs; WorkManager persistent and periodic work documentation.

GitHub Docs: Building and testing Java with Gradle. Full reference links and provenance are listed in REFERENCES.md.
