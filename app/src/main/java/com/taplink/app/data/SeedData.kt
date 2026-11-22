package com.taplink.app.data

import com.taplink.app.domain.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

internal object SeedData {
    private const val DAY = 86_400_000L
    val merchants = listOf(
        Merchant("campus-cafe", "Campus Cafe", Category.FOOD, "Sai Kung", "Demo coffee, sandwiches, and student lunches."),
        Merchant("harbour-metro", "Harbour Metro", Category.TRANSPORT, "Kowloon City", "Fictional urban rail merchant; no real tickets."),
        Merchant("city-bus", "City Bus", Category.TRANSPORT, "Sai Kung", "Fictional local bus merchant."),
        Merchant("harbour-grocer", "Harbour Grocer", Category.SHOPPING, "Kwun Tong", "Demo groceries and everyday essentials."),
        Merchant("bay-books", "Bay Bookshop", Category.SHOPPING, "Eastern", "Demo books and stationery."),
        Merchant("campus-print", "Campus Print", Category.SERVICES, "Sai Kung", "Demo printing and copying services.")
    )

    private val locations = listOf(
        ServiceLocation("campus-topup", "Campus Demo Top-up Point", LocationType.TOP_UP, "Sai Kung",
            "Clear Water Bay campus concourse (demo)", 22.3364, 114.2655, "08:00–22:00",
            "Seeded demo location. No real top-up service is provided."),
        ServiceLocation("campus-cafe-location", "Campus Cafe Demo", LocationType.MERCHANT, "Sai Kung",
            "Clear Water Bay campus atrium (demo)", 22.3356, 114.2637, "07:30–19:00",
            "Fictional cafe near campus. Location is approximate."),
        ServiceLocation("tseung-kwan-o", "Bay Transit Demo Stop", LocationType.TRANSPORT, "Sai Kung",
            "Tseung Kwan O town centre (demo)", 22.3070, 114.2600, "06:00–00:00",
            "Sample transport point, not a verified transport directory."),
        ServiceLocation("kowloon-metro", "Harbour Metro Demo Station", LocationType.TRANSPORT, "Kowloon City",
            "Kowloon waterfront (demo)", 22.3020, 114.1880, "06:00–00:30",
            "Fictional station for the nearby-services demonstration."),
        ServiceLocation("kwun-tong-topup", "Kwun Tong Demo Service Point", LocationType.TOP_UP, "Kwun Tong",
            "Kwun Tong promenade area (demo)", 22.3092, 114.2232, "09:00–21:00",
            "Seeded service point. No live availability is promised."),
        ServiceLocation("bay-books-location", "Bay Bookshop Demo", LocationType.MERCHANT, "Eastern",
            "Quarry Bay neighbourhood (demo)", 22.2882, 114.2110, "10:00–20:00",
            "Fictional shop. Open an external map for the approximate area.")
    )

    suspend fun ensureCatalog(dao: TapLinkDao, now: Long) {
        dao.insertMerchants(merchants.map(::MerchantEntity))
        dao.insertLocations(locations.map(::LocationEntity))
        // IGNORE preserves original expiry dates, favorites, and redeemed coupons on refresh.
        dao.insertOffers(listOf(
            Offer("coffee-break", "campus-cafe", "A little coffee break", "Save HK$8 on a demo cafe order.",
                "Demo coupon only. One redemption. No cash value or real merchant acceptance.", now + 2 * DAY),
            Offer("weekend-essentials", "harbour-grocer", "Weekend essentials", "Enjoy a fictional 10% grocery discount.",
                "Educational demonstration only. One use; not deducted from wallet payments.", now + 14 * DAY),
            Offer("next-chapter", "bay-books", "Your next chapter", "A demo HK$15 bookshop coupon.",
                "No real goods, discounts, or merchant affiliation. Redeem once before expiry.", now + 30 * DAY)
        ).map(::OfferEntity))
    }

    suspend fun populate(dao: TapLinkDao, now: Long) {
        ensureCatalog(dao, now)
        val previousMonth = YearMonth.from(Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Hong_Kong")))
            .minusMonths(1).atDay(12).atStartOfDay(ZoneId.of("Asia/Hong_Kong")).toInstant().toEpochMilli()
        seedCard(dao, Card("card-campus", "Campus", "•••• 2048", 0, false, now - 60 * DAY, "#167D68"), listOf(
            Event(60000, null, now - 50 * DAY),
            Event(4250, "campus-cafe", now - 10 * DAY),
            Event(1950, "harbour-metro", now - 8 * DAY),
            Event(2950, "bay-books", now - 6 * DAY),
            Event(800, "campus-print", now - 4 * DAY),
            Event(2200, "campus-cafe", now - 2 * DAY),
            Event(5000, "city-bus", now - 3_600_000L)
        ))
        seedCard(dao, Card("card-commute", "Commute", "•••• 8192", 0, false, now - 60 * DAY + 1, "#435D9B"), listOf(
            Event(80000, null, now - 55 * DAY),
            Event(35000, "harbour-metro", previousMonth),
            Event(18000, "city-bus", now - DAY)
        ))
        seedCard(dao, Card("card-weekend", "Weekend", "•••• 4096", 0, true, now - 60 * DAY + 2, "#AD7044"),
            listOf(Event(20000, null, now - 30 * DAY)))
        dao.savePreferences(PreferencesEntity(value = AppPreferences(selectedCardId = "card-campus")))
        dao.insertMessage(MessageEntity(WalletMessage(
            "welcome", "Welcome to TapLink", "Your three demo cards are ready. All balances, payments, passes, and coupons are simulated.",
            MessageType.INFO, now, false
        )))
    }

    private data class Event(val amount: Long, val merchantId: String?, val time: Long)

    private suspend fun seedCard(dao: TapLinkDao, card: Card, events: List<Event>) {
        dao.insertCard(CardEntity(card))
        var balance = 0L
        events.sortedBy { it.time }.forEachIndexed { index, event ->
            val merchant = merchants.find { it.id == event.merchantId }
            balance += if (merchant == null) event.amount else -event.amount
            check(balance in 0..Money.MAX_BALANCE_CENTS)
            val id = "seed-${card.id}-$index"
            val transaction = WalletTransaction(id, id, card.id,
                if (merchant == null) TransactionType.TOP_UP else TransactionType.PAYMENT,
                event.amount, balance, merchant?.id, merchant?.name ?: "Demo funding",
                merchant?.category ?: Category.OTHER, event.time, "Seeded simulated activity")
            dao.insertTransaction(TransactionEntity(transaction))
            dao.insertOperation(OperationEntity(id, "seed", id, null, null, balance))
        }
        dao.updateCard(CardEntity(card.copy(balanceCents = balance)))
    }
}
