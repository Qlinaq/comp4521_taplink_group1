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
| E02b | Coupon spend | "Use" on a redeemed coupon opens the payment form with that merchant and coupon chosen; a HK$20 payment with a HK$5 coupon charges HK$15.00, the receipt shows subtotal/discount/coupon, and the coupon then reads Used. |
| E02c | Coupon guards | A coupon for another merchant, below its minimum spend, already used, or unredeemed cannot be applied; a percentage coupon is capped and never leaves a zero charge. |
| E02d | Per-wallet coupons | A coupon spent in the guest wallet still shows as usable after signing in to another account; signing out and back in shows that wallet's own states. |
| E03 | Fictional subsidy | Completed-month rule and cap are exact; duplicate claim does not credit again. |
| E04 | Demo catalogue refresh | Preserves existing card balances, preferences, favorites and redemptions. |

| A01 | Sign up with an email | A six-digit code appears in the simulated inbox; verifying it signs the user in and Settings shows the masked address. |
| A02 | Sign up with a Hong Kong phone number | `91234567`, `85291234567` and `+85291234567` resolve to one account; the masked form hides the middle digits. |
| A03 | Weak or mismatched password | Rejected with a field-level message; no account row is created and no session starts. |
| A04 | Duplicate identifier | A second sign-up with the same email or phone reports that the identifier is taken; the original account is unchanged. |
| A05 | Wrong code, expired code, reused code | Each is rejected with its own message; a consumed code cannot verify twice. |
| A06 | Resend cooldown and send cap | A second send inside 60 s is refused; the sixth send in an hour is refused; a new code invalidates the previous one. |
| A07 | Sign in with wrong password, five times | The message never reveals whether the identifier exists; after five failures the identifier is locked and the delay doubles on further failures. |
| A08 | Password reset | Reset with a valid code sets the new password, ends the old session, and the old password no longer signs in. |
| A09 | Change password | Requires the current password; success collapses the form; the next sign-in uses the new password only. |
| A10 | Delete account | Requires the current password; on success the credentials are removed, the session ends and the welcome screen appears. |
| A11 | Session persistence | Closing and reopening the app while signed in returns to the wallet without asking again; a session older than 30 days is treated as expired. |
| A12 | Guest access | "Continue as guest" reaches the wallet without an account; the guest flag persists across restart; creating an account from guest raises the welcome flow instead. |
| A13 | Stored credential audit | Reading the account row directly from the database shows a Base64 hash, a salt and an iteration count, and no plaintext password anywhere in the row. |
| L01 | Switch to Traditional Chinese | Navigation, Settings, Activity, chart labels, dates and every button change immediately; no restart and no lost navigation state. |
| L02 | Switch to Simplified Chinese | Same, with Simplified vocabulary; the language choice survives a restart. |
| L03 | Dates in Chinese | Dates read year-first (`2026年11月15日`) and month headings follow (`2026年11月`), not "15 11月 2026". |
| L04 | Long translated text | Longer Chinese strings wrap without clipping; no primary action is pushed off screen. |
| L05 | Resource parity | Adding a key to one locale only fails the build; a lost `%1$s` placeholder fails the build. |

| N01 | Notification master switch off | No alert reaches Android, but the message still appears in the in-app inbox. |
| N02 | Android permission denied | The screen states that system notifications are not allowed, offers the system settings shortcut, and posting fails quietly rather than crashing. |
| N03 | Android permission granted | Reopening the screen reports "allowed"; "Send a test notification" posts one simulated alert. |
| N04 | Per-category switch | Turning off one category suppresses only that kind; the other four still post. |
| N05 | Committed transaction in the inbox | A top-up, payment or pass purchase adds exactly one inbox row with the amount, and repeating the same operation ID does not add a second. |
| N06 | Inbox in Chinese | A stored English row renders in Traditional or Simplified Chinese without rewriting the database. |
| C10 | Contact channel — WhatsApp | Tapping Open leaves the app for WhatsApp with the number and a pre-written message; nothing is sent automatically. |
| C11 | Contact channel — hotline | Tapping Call opens the dialer with 2520 1314 filled in; no call is placed. |
| C12 | Contact disclaimer | The screen states that TapLink is a coursework prototype and not an Octopus or government service. |
| X01 | Elderly master switch on | Large text, high contrast, read-aloud and the simplified flow all switch on in one tap, and a banner confirms the mode. |
| X02 | Elderly master switch off | All four switch off together; the stored preferences confirm it (not only the screen). |
| X03 | Individual switches | Turning read-aloud on while keeping large text off is possible; the individual switches never move each other. |
| X04 | 150% text layout | Home, activity, inbox, settings and chat stay readable; the transaction amount moves under the merchant instead of squeezing the row. |
| X05 | High contrast | Text and controls remain legible in light and dark; panels and chat bubbles are outlined. |
| X06 | Read-aloud | The app-bar speaker reads a summary of the current screen in the chosen language; without a Chinese engine it reports unavailability instead of failing silently. |
| X07 | Simplified home | Home shows balance, top up, pay, three recent records and a contact row, with card management only in Settings. |
| X08 | Sign language | Four chapters each show a complete written transcript; without a clip the player is disabled and says so. |
| S01 | Swipe home cards | Swiping left or right changes the card and the dots, arrows and "Card n of m" follow; the selected card persists into Settings and payments. |
| S02 | Frozen card in the carousel | The frozen card keeps its badge and cannot be paid from. |
| Q01 | AI support — normal answer | A question receives a short answer in the chosen language and the remaining-question counter drops by one. |
| Q02 | AI support — account matter | A refund, lost-card or account question is handed to WhatsApp 2520 1314 instead of being answered definitively. |
| Q03 | AI support — secret in the message | Password- or code-shaped input is refused before anything leaves the device. |
| Q04 | AI support — quota exhausted | After the twentieth question of the Hong Kong day the chat answers from the local FAQ and says so. |
| Q05 | AI support — offline | With no network or no key, the FAQ fallback answers and the reply names the fallback rather than showing an error code. |
| Q06 | AI support — prompt rules | The system prompt still forbids real-money claims, secret requests and promises, and requires answering in the chosen language. |
| W01 | Wallet per account — switch | Account A tops up, signs out, account B signs in: B sees its own single empty card, not A's balance. Signing back in to A restores A's wallet. |
| W02 | Wallet per account — write isolation | A top-up made while B is signed in changes only B's card; A's stored balance is unchanged. |
| W03 | Wallet per account — cross-owner card id | Paying with a card id that belongs to another owner fails with "card could not be found" instead of debiting it. |
| W04 | Wallet per account — new account seed | A newly confirmed account starts with one card at HK$0.00 and one welcome message, never a copy of the demo wallet. |
| W05 | Wallet per account — upgrade | Installing over a version-4 database keeps the existing cards, balance and history under the account signed in at the time, once, and a second account never inherits them. |
| W06 | Wallet per account — background work | Reminders, content refresh and the widget use the signed-in account's wallet after a process restart, not the demo wallet. |
| K01 | Account entry | The four root screens show an account button in the leading app-bar slot (first letter, or a person glyph for the demo wallet); tapping it opens the account screen, and other screens keep Back there. |
| K02 | Keyboard cannot type the language | When the enabled keyboards cannot type the chosen language, the support screen says so and offers a shortcut to the system keyboard settings; the notice disappears after a keyboard is added, without restarting the app. |
| K03 | Chinese input end to end | With a Chinese keyboard installed, a Chinese question can be typed, sent and answered in Chinese. |
| K04 | Keyboard covers all three interface languages | The device offers a keyboard for English, 简体中文 and 繁體中文 (on the test AVD: `English (US)`, `简体中文 (拼音)`, `繁體中文 (台灣, 注音)`), so each interface language can be typed into with a matching layout. |
| Y01 | Heading form on the tab roots | Home, Activity, Explore and More each open on the same heading: a pill eyebrow, a semi-bold headline and, except on Home, a muted supporting line; the block sits on a tinted card. |
| Y02 | Title punctuation | Page titles render without a trailing full stop, while the supporting sentence under a title keeps its own punctuation. |
| Y03 | Ring chart legibility | The two centre lines of the Activity donut fit inside the stroke at default and 150% text, with the month bolder than the note under it. |
| Y04 | Chinese punctuation in the support field | With a Chinese keyboard, `，` and `。` can be typed into the support field and the message is sent with them intact. |

Tests should use dedicated test databases or the TapLink demo installation, not unrelated user apps. Record the APK hash and tested source revision when preparing a handoff.
