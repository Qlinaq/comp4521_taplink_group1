# TapLink data and domain API

Stable integration contract. All public types below live in `com.taplink.app.domain`; the Android entry point lives in `com.taplink.app.data`. All displayed content is English and all money is simulated HKD. Monetary values are `Long` integer cents; timestamps are epoch milliseconds. IDs are `String`.

## Entry point and observable state

```kotlin
val repository: TapLinkRepository = TapLinkContainer.repository(context.applicationContext)
// Import com.taplink.app.data.TapLinkContainer
// Call in a coroutine to explicitly await initial demo data (flows also initialize automatically):
repository.initialize()
```

`TapLinkContainer.repository(Context)` returns one application-scoped Room repository. It needs no custom Application subclass. Collect flows with Compose lifecycle APIs; collect cards/transactions/etc with `emptyList()` initially, preferences with `AppPreferences()`, and selectedCard with `null` initially. No operation blocks the main thread. Every mutation returns `WalletResult<T>`; render `Error.message`, or use `Success.value`. A cancelled coroutine propagates cancellation.

```kotlin
interface TapLinkRepository {
    val cards: Flow<List<Card>>
    val selectedCard: Flow<Card?>
    val transactions: Flow<List<WalletTransaction>> // newest timestamp, then persisted ledger sequence
    val merchants: Flow<List<Merchant>>
    val locations: Flow<List<ServiceLocation>>
    val messages: Flow<List<WalletMessage>> // newest first
    val preferences: Flow<AppPreferences>
    val passes: Flow<List<TransportPass>>
    val offers: Flow<List<Offer>>
    val subsidyClaims: Flow<List<SubsidyClaim>>
    suspend fun initialize()
    suspend fun createCard(name: String): WalletResult<Card> // starts at zero
    suspend fun renameCard(cardId: String, name: String): WalletResult<Unit>
    suspend fun setCardFrozen(cardId: String, frozen: Boolean): WalletResult<Unit>
    suspend fun selectCard(cardId: String): WalletResult<Unit> // persists default selection
    suspend fun topUp(cardId: String, amountCents: Long, operationId: String): WalletResult<Receipt>
    suspend fun pay(cardId: String, merchantId: String, amountCents: Long, operationId: String): WalletResult<Receipt>
    suspend fun buyPass(cardId: String, operationId: String): WalletResult<Receipt>
    suspend fun updatePreferences(preferences: AppPreferences): WalletResult<Unit>
    suspend fun transformPreferences(change: (AppPreferences) -> AppPreferences): WalletResult<Unit>
    suspend fun markMessageRead(messageId: String): WalletResult<Unit>
    suspend fun markAllMessagesRead(): WalletResult<Unit>
    suspend fun setOfferFavorite(offerId: String, favorite: Boolean): WalletResult<Unit>
    suspend fun redeemOffer(offerId: String): WalletResult<Offer>
    suspend fun claimSubsidy(cardId: String, month: String, operationId: String): WalletResult<Receipt> // month YYYY-MM
    suspend fun refreshDemoContent(): WalletResult<Unit> // local, preserves user state
    suspend fun generateReminders(now: Long = System.currentTimeMillis()): WalletResult<Int> // new messages count
    suspend fun getReceipt(operationId: String): Receipt?
    fun observeTransactions(filter: TransactionFilter = TransactionFilter()): Flow<List<WalletTransaction>>
}
```

Callers generate `UUID.randomUUID().toString()` once when starting a confirmation attempt, retain that operation ID across repeated taps/retries, and generate a new one for a new intended operation. The same ID and inputs return the original receipt without another debit, credit, pass, or alert. Reuse with different inputs returns `IDEMPOTENCY_CONFLICT`. UI owns review/confirmation; calling a mutation commits it.

Use `transformPreferences { latest -> latest.copy(theme = ThemeMode.DARK) }` for UI/platform preference edits, including amount settings. The callback runs once, inside the serialized Room transaction, after reading the latest preferences. Keep it quick and side-effect-free. Validation and persistence occur in that same transaction; invalid changes leave all settings unchanged. Do not capture and return a previously collected preferences model: copy the callback's `latest` argument. `updatePreferences` remains a whole-model replacement API for deliberate replacement; it cannot preserve omitted changes from a stale snapshot. `selectCard` already edits the latest preferences atomically.

Independent observable flows can emit at different times after one atomic database commit. Do not assume separately collected cards, transactions, and preferences form one transactional snapshot. `selectedCard` uses a single joined query; a `Receipt` provides an immutable, consistent result for its operation.

### Cancellation and outcome recovery

`CancellationException` is not proof that a money operation failed. Room rolls back when an exception or cancellation is observed before successful commit. After commit, coroutine dispatch back to the caller can still discard success and deliver cancellation; the money operation then remains committed in full.

Retain the original operation ID **and its inputs** across lifecycle cancellation. From a new active coroutine, query `getReceipt(operationId)` or retry the same method with the same ID and inputs. Both recover a committed receipt without another debit or credit. A null receipt means no committed result was found at that read; a same-ID retry is safe whether the earlier call is still finishing or rolled back. Reconcile this pending attempt before allowing a replacement confirmation with a fresh ID. Never display cancellation alone as a definitive payment failure. Cancelling a review dialog before invoking the repository has no financial effect.

## Exact public model constructors

```kotlin
enum class TransactionType { PAYMENT, TOP_UP, AUTO_RELOAD, PASS_PURCHASE, SUBSIDY_CREDIT }
enum class Category { TRANSPORT, FOOD, SHOPPING, SERVICES, OTHER }
enum class LocationType { TOP_UP, MERCHANT, TRANSPORT }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MessageType { INFO, LOW_BALANCE, PASS_EXPIRY, OFFER_EXPIRY }
enum class WalletError {
    INVALID_AMOUNT, INVALID_NAME, INVALID_PREFERENCES, NOT_FOUND, CARD_FROZEN,
    INSUFFICIENT_BALANCE, BALANCE_LIMIT, IDEMPOTENCY_CONFLICT, INVALID_OPERATION_ID,
    ALREADY_REDEEMED, OFFER_EXPIRED, PASS_ALREADY_ACTIVE, INVALID_MONTH,
    ALREADY_CLAIMED, NOTHING_TO_CLAIM, STORAGE_ERROR
}
sealed interface WalletResult<out T> {
    data class Success<T>(val value: T) : WalletResult<T>
    data class Error(val code: WalletError, val message: String) : WalletResult<Nothing>
}
data class Card(val id: String, val name: String, val maskedNumber: String,
    val balanceCents: Long, val isFrozen: Boolean, val createdAt: Long, val colorHex: String)
data class Merchant(val id: String, val name: String, val category: Category,
    val district: String, val description: String)
data class WalletTransaction(val id: String, val operationId: String, val cardId: String,
    val type: TransactionType, val amountCents: Long, val balanceAfterCents: Long,
    val merchantId: String?, val merchantName: String, val category: Category,
    val createdAt: Long, val note: String) {
    val isDebit: Boolean // PAYMENT or PASS_PURCHASE
    val signedAmountCents: Long
}
data class Receipt(val transaction: WalletTransaction, val autoReload: WalletTransaction?,
    val finalBalanceCents: Long, val pass: TransportPass? = null) {
    fun shareText(): String // clearly labelled simulated; includes final balance and optional reload
}
data class AppPreferences(
    val selectedCardId: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val balanceVisible: Boolean = true,
    val widgetBalanceVisible: Boolean = false,
    val lowBalanceAlerts: Boolean = true,
    val lowBalanceThresholdCents: Long = 5000,
    val autoReloadEnabled: Boolean = false,
    val autoReloadThresholdCents: Long = 5000,
    val autoReloadAmountCents: Long = 10000,
    val remindersEnabled: Boolean = true,
    val expiryRemindersEnabled: Boolean = true,
    val demoRefreshEnabled: Boolean = true,
    val shakeToToggleEnabled: Boolean = false,
    val manualDistrict: String = "Sai Kung")
data class WalletMessage(val id: String, val title: String, val body: String,
    val type: MessageType, val createdAt: Long, val isRead: Boolean,
    val cardId: String? = null, val transactionId: String? = null)
data class ServiceLocation(val id: String, val name: String, val type: LocationType,
    val district: String, val address: String, val latitude: Double, val longitude: Double,
    val openingHours: String, val description: String)
data class TransportPass(val id: String, val cardId: String, val name: String,
    val priceCents: Long, val validFrom: Long, val validUntil: Long, val transactionId: String) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean
}
data class Offer(val id: String, val merchantId: String, val title: String,
    val description: String, val terms: String, val expiresAt: Long,
    val isFavorite: Boolean = false, val redeemedAt: Long? = null, val couponCode: String? = null)
data class SubsidyClaim(val id: String, val cardId: String, val month: String,
    val transportSpendingCents: Long, val amountCents: Long, val claimedAt: Long, val transactionId: String)
data class TransactionFilter(val cardId: String? = null, val category: Category? = null,
    val type: TransactionType? = null, val fromInclusive: Long? = null,
    val toExclusive: Long? = null, val query: String = "")
data class SpendingSummary(val totalCents: Long, val byCategory: Map<Category, Long>)
```

## Pure domain helpers and rules

```kotlin
object Money {
    const val MAX_BALANCE_CENTS: Long = 100_000_000L
    fun parseCents(input: String): Long? // positive decimal, at most 2 places; no floating point
    fun format(cents: Long): String // e.g. HK$123.45
}
object WalletAnalytics {
    fun filter(transactions: List<WalletTransaction>, filter: TransactionFilter): List<WalletTransaction>
    fun spending(transactions: List<WalletTransaction>): SpendingSummary
    fun monthlySpending(transactions: List<WalletTransaction>, month: YearMonth,
        zone: ZoneId = ZoneId.of("Asia/Hong_Kong")): SpendingSummary
    fun fictionalSubsidy(transportSpendingCents: Long): Long
}
object PassCatalog {
    const val MONTHLY_PASS_PRICE_CENTS: Long = 30000L
    const val MONTHLY_PASS_NAME: String = "TapLink Demo Monthly Pass"
}
```

Payment and pass purchase require an unfrozen card and enough balance **before** reload. When enabled, one reload occurs after a successful debit only if the remaining balance is strictly below the reload threshold. Debit, optional reload, final balance, receipt, pass, and in-app low-balance alert commit in one Room transaction. Alerts use the final committed balance and respect `lowBalanceAlerts`. Frozen cards reject both debits and top-ups. Credit/debit amounts must be positive and balance must stay in `0..MAX_BALANCE_CENTS`; invalid settings and overflow are rejected. Failed transactions and cancellation before commit roll back all their writes. Cancelling a confirmation before invoking the repository does nothing. If a caller loses the response after commit, retry with the same operation ID or call `getReceipt` to recover the committed result.

Passes last one calendar month in Hong Kong time; a card cannot buy another while one is active. All spending totals include payment and pass-purchase debits; credits/reloads are excluded. History is ordered by `createdAt DESC, ledgerSequence DESC`: an internal SQLite-generated sequence preserves real ledger insertion order when timestamps tie, including the reload after its debit and separate operations in one millisecond. This sequence does not change public model constructors or timestamps. History date bounds are inclusive start, exclusive end, and query searches merchant name case-insensitively. Fictional subsidy is 20% of a completed month's transport spending above HK$200, capped at HK$100, rounded down to cents; at most one claim per card/month, no real entitlement.

Offer redemption persists a demo coupon and timestamp, rejects expiry and repeat redemption, and never moves money. Reminders are deduplicated by target/expiry; scheduler and OS notification permission work belong to the integration layer. Refresh is local and does not require network or reset balances, preferences, favorites, or redemptions.

## Build and ownership

Application ID / namespace `com.taplink.app`; AGP 8.7.2, Kotlin 2.0.21, KSP 2.0.21-1.0.25, Compose BOM 2024.10.01, Room 2.6.1, Gradle 8.10.2; compile/target 35, min 29. Gradle runs with JDK 21; bytecode targets JVM 17. UI owns `MainActivity` and its packages. Foundation owns build/wrapper/manifest/base resources plus `data`, `domain`, and their `test`/`androidTest` files. No tests belong in release sources.

## Foundation file inventory

Paths below are relative to this project root. `local.properties` is generated for this workstation and ignored by Git. The wrapper scripts, JAR, and properties are the only files copied from the cached reference project. No reference application source, identity, or cache is copied.

```text
.gitignore
API_CONTRACT.md
build.gradle.kts
settings.gradle.kts
gradle.properties
gradlew
gradlew.bat
local.properties
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/drawable/ic_taplink.xml
app/src/main/res/xml/data_extraction_rules.xml
app/src/main/java/com/taplink/app/domain/Models.kt
app/src/main/java/com/taplink/app/domain/TapLinkRepository.kt
app/src/main/java/com/taplink/app/domain/Money.kt
app/src/main/java/com/taplink/app/domain/WalletRules.kt
app/src/main/java/com/taplink/app/domain/WalletAnalytics.kt
app/src/main/java/com/taplink/app/data/Entities.kt
app/src/main/java/com/taplink/app/data/TapLinkDao.kt
app/src/main/java/com/taplink/app/data/TapLinkDatabase.kt
app/src/main/java/com/taplink/app/data/TapLinkContainer.kt
app/src/main/java/com/taplink/app/data/SeedData.kt
app/src/main/java/com/taplink/app/data/RoomTapLinkRepository.kt
app/src/test/java/com/taplink/app/domain/MoneyTest.kt
app/src/test/java/com/taplink/app/domain/WalletRulesTest.kt
app/src/test/java/com/taplink/app/domain/WalletAnalyticsTest.kt
app/src/androidTest/java/com/taplink/app/data/RoomTapLinkRepositoryTest.kt
```

KSP exports the Room schema to `app/schemas/com.taplink.app.data.TapLinkDatabase/1.json` during the first app compilation; include the generated schema in version control. Independent compiler-check output is confined to ignored `app/build/domain-verification/` and `app/build/foundation-verification/` folders.

## Foundation verification handoff

- Pure domain tests: **23 passed**, using cached Kotlin 2.0.21, JDK 21, and JUnit 4.13.2 independently of Gradle. Includes 10,000 randomized affordable-payment checks.
- All `data` and `domain` Kotlin sources compile against the cached Android 35 and Room 2.6.1 libraries. This checks Kotlin types; generated Room code still requires the coordinated KSP build.
- `RoomTapLinkRepositoryTest`: **24 device tests authored**, covering seeding and ledger reconciliation, card management, atomic preference transforms with stale/concurrent callers, durable receipts, concurrent idempotency, concurrent overspending, rejected operations, one reload, final-balance alerts, tied-timestamp ledger sequence, SQL-trigger failure rollback at several write stages, cancellation before commit and receipt recovery after commit, pass validity, coupons, reminders, subsidy claims, and closing/reopening a file-backed database. New device cases require the coordinated emulator run; authoring/compiling them is not a passing runtime result.
- Main owns the coordinated Gradle build, lint, and emulator test run. Foundation has not claimed those checks passed. Standard commands: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest` with JDK 21 and an API 35 emulator.
