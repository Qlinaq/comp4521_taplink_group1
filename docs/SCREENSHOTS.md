# Installed application screenshots

Captured from Android API 35 emulators during the verification sessions — rows 1–13 from the Pixel 6 profile (wallet round), rows 14–20 from the `Small_Phone` profile (accounts and language round), rows 21–30 from the same `Small_Phone` profile (notifications, accessibility and support round), rows 31–35 from the same profile (per-account wallets and the keyboard diagnosis). These are actual app screens, not generated mockups. The demonstrated Demo card was later renamed Showcase.

| Screen | Screenshot |
| --- | --- |
| Seeded dashboard | [Home](screenshots/01-home.png) |
| Explicit confirmation | [Top-up review](screenshots/02-top-up-review.png) |
| Persisted receipt | [Top-up receipt](screenshots/03-top-up-receipt.png) |
| Reload preferences | [Settings](screenshots/04-reload-settings.png) |
| Retained form in landscape | [Landscape](screenshots/05-form-landscape.png) |
| Debit and automatic reload | [Payment receipt](screenshots/06-payment-receipt.png) |
| Activity overview | [All-card activity](screenshots/07-activity.png) |
| Debit-only total after card filtering | [Filtered spending](screenshots/08-filtered-spending.png) |
| Search with no results | [Empty history](screenshots/09-empty-history.png) |
| Permission denial fallback | [Nearby demo map](screenshots/10-nearby-map.png) |
| Unaffordable payment rejected | [Insufficient balance](screenshots/11-insufficient-balance.png) |
| Frozen-card rejection | [Frozen card](screenshots/12-frozen-card.png) |
| Final theme and persisted Showcase card | [Dark dashboard](screenshots/13-dark-home.png) |
| Sign-in gate, Simplified Chinese | [Welcome](screenshots/14-welcome-cn.png) |
| Simulated verification code | [Verify](screenshots/15-verify-simulated-cn.png) |
| Signed-in account section | [Account](screenshots/16-account-settings-cn.png) |
| Wallet in Traditional Chinese | [Home zh-TW](screenshots/17-home-zh-tw.png) |
| Localised chart and month heading | [Activity zh-TW](screenshots/18-activity-zh-tw.png) |
| Localised dates in the ledger | [Transactions zh-TW](screenshots/19-transactions-zh-tw.png) |
| Language switch in Settings | [Settings zh-TW](screenshots/20-settings-zh-tw.png) |
| Swiped to the second card (dots and position follow) | [Home card 2](screenshots/21-home-swipe-card2.png) |
| Swiped to the frozen card | [Home card 3 frozen](screenshots/22-home-swipe-card3-frozen.png) |
| Elderly mode on: master switch and banner | [Accessibility](screenshots/23-accessibility-settings.png) |
| Large text and high contrast switches | [Display](screenshots/23b-accessibility-display.png) |
| Read-aloud switch and sample | [Voice](screenshots/23c-accessibility-voice.png) |
| Simplified home at 150% text | [Elderly home](screenshots/24-elderly-home.png) |
| Simplified home actions | [Elderly home actions](screenshots/24b-elderly-home-actions.png) |
| Contact row on the simplified home | [Elderly contact row](screenshots/24c-elderly-contact-row.png) |
| Notification master switch, permission state, test alert | [Notifications](screenshots/25-notification-settings.png) |
| Five independent category switches | [Categories](screenshots/25b-notification-categories.png) |
| Contact channels with WhatsApp and hotline | [Contact us](screenshots/26-contact-us.png) |
| Online channels and sign-language row | [Contact online](screenshots/26b-contact-online-hours.png) |
| Prototype disclaimer | [Disclaimer](screenshots/26c-contact-disclaimer.png) |
| AI answer from `deepseek-flash` and the daily counter | [Text support](screenshots/27-support-chat.png) |
| Hand-off footer and AI disclaimer | [Chat footer](screenshots/27b-support-chat-footer.png) |
| Sign-language chapters with the honest empty state | [Sign language](screenshots/28-sign-language.png) |
| Localised inbox row in Traditional Chinese | [Inbox zh-TW](screenshots/29-inbox-zh-tw.png) |
| Test notification in the Android shade | [Notification shade](screenshots/30-notification-shade.png) |
| Demo wallet with the account button in the app bar | [Guest wallet](screenshots/31-guest-wallet-account-entry.png) |
| A newly signed-in account: one empty card, not the demo wallet | [New account wallet](screenshots/32-account-wallet-new-account.png) |
| The app noticing that the installed keyboard cannot type Chinese | [Keyboard notice](screenshots/33-support-keyboard-hint.png) |
| Android's keyboard list showing only English (US) | [Keyboard list](screenshots/34-android-keyboard-only-english.png) |
| A Chinese question typed with a Chinese keyboard, answered in Chinese | [Chinese input](screenshots/35-support-chinese-input.png) |
| Home: pill eyebrow and semi-bold headline on the tinted hero card, no subtitle | [Home heading](screenshots/36-home-hero-heading.png) |
| Activity: the same heading, and the ring text fitted and bold inside the ring | [Activity heading and chart](screenshots/37-activity-chart-fit.png) |
| Explore: the same heading form on a tab root | [Explore heading](screenshots/38-explore-hero-heading.png) |
| More: the same heading form on a tab root | [More heading](screenshots/39-more-hero-heading.png) |
| Chinese punctuation: `，` and `。` typed with the pinyin keyboard in the support field | [Chinese punctuation](screenshots/40-support-chinese-punctuation.png) |
| Gboard after adding 繁體中文 (台灣) next to English (US) and 简体中文 | [Three keyboard languages](screenshots/41-keyboard-three-languages.png) |

Screens are scrollable; a single screenshot shows one viewport, not every control on a page. The Android status bar uses the emulator's timezone; transaction times explicitly use Hong Kong time.

Rows 23–24 were captured with elderly mode on (150% text, high contrast, read-aloud, simplified
flow); rows 23b, 23c and 24c show the same screens scrolled so that the individual switches, the
voice controls and the contact row are visible. Every other row was captured at the default text
size. Where a screenshot shows a note cut at the bottom edge, it is the list continuing under the
pinned input row, not missing content — the same screen scrolled one step further shows it in
27b.

Rows 33–35 record a device diagnosis rather than a feature: the emulator's only keyboard offered
`English (US)`, so the app explained the gap and linked to the system screen (33), the system list
confirms the missing layout (34), and after adding a Chinese keyboard the same field accepts Chinese
and the assistant answers in Chinese (35).

Rows 36–41 record the presentation round (F28). The three tab roots now open on the same heading
card as Home, the Activity ring text is bold and fitted inside the ring, and row 40 shows full-width
Chinese punctuation in the support field. Row 41 is device preparation rather than app output: the
test device now carries `English (US)`, `简体中文 (拼音)` and `繁體中文 (台灣, 注音)`, so the
Traditional Chinese interface can be typed into with a matching keyboard instead of a Simplified one.
